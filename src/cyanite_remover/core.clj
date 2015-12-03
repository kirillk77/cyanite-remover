(ns cyanite-remover.core
  (:import (java.io RandomAccessFile)
           (org.joda.time.format PeriodFormatterBuilder))
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clj-progress.core :as prog]
            [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [clj-time.coerce :as timec]
            [clj-time.format :as timef]
            [cyanite-remover.logging :as clog]
            [cyanite-remover.metric-store :as mstore]
            [cyanite-remover.path-store :as pstore]
            [cyanite-remover.utils :as utils]
            [com.climate.claypoole :as cp]))

(def ^:const default-jobs 1)
(def ^:const default-obsolete-metrics-threshold 2678400)

(def ^:const pbar-width 35)

(def inspecting? (atom false))

(def list-metrics-str "Path: %s, rollup: %s, period: %s, time: %s, data: %s")
(def starting-str "==================== Starting ====================")

(defprotocol MetricProcessor
  "Metric processing protocol."
  (mp-get-title [this])
  (mp-get-paths [this])
  (mp-get-paths-rollups [this])
  (mp-process [this rollup period path from to])
  (mp-show-stats [this errors]))

(defprotocol PathProcessor
  "Path processing protocol."
  (pp-get-title [this])
  (pp-process [this path])
  (pp-show-stats [this errors]))

(defprotocol Tree
  "Paths tree protocol."
  (t-add-path [this path leaf?])
  (t-get [this path])
  (t-path-leaf? [this path])
  (t-path-empty? [this path])
  (t-delete-path [this path])
  (t-get-raw-tree [this]))

(defprotocol TreeProcessor
  "Tree processor protocol."
  (tp-get-title [this])
  (tp-get-paths [this])
  (tp-process-path [this path])
  (tp-get-data [this])
  (tp-show-stats [this]))

(def paths-info (atom nil))

(defn- set-inspecting-on!
  "Set the inspecting flag on."
  []
  (swap! inspecting? (fn [_] true)))

(defn- get-sort-or-dummy-fn
  "Get sort or dummy function."
  [sort?]
  (if sort? sort identity))

(defn- lookup-paths
  "Lookup paths."
  [pstore tenant leafs-only limit-depth paths exclude-paths sort-paths?
   & [sidefx-fn]]
  (let [lookup-fn #(pstore/lookup pstore tenant leafs-only limit-depth %
                                  exclude-paths)
        sidefx-wrapper (if sidefx-fn #(do (sidefx-fn %) %) identity)
        sort (get-sort-or-dummy-fn sort-paths?)]
    (sort (map :path (map sidefx-wrapper (flatten (map lookup-fn paths)))))))

(defn- get-paths
  "Get paths."
  [pstore tenant paths-to-lookup exclude-paths sort-paths? & [sidefx-fn]]
  (let [paths (lookup-paths pstore tenant false false paths-to-lookup
                            exclude-paths sort-paths? sidefx-fn)
        title "Getting paths"]
    (newline)
    (clog/info (str title "..."))
    (when-not @clog/print-log?
      (println title)
      (prog/set-progress-bar! "[:bar] :done")
      (prog/config-progress-bar! :width pbar-width)
      (prog/init 0)
      (doseq [_ paths]
        (prog/tick))
      (prog/done))
    (clog/info (format "Found %s paths" (count paths)))
    paths))

(defn- combine-paths-rollups
  "Combine paths and rollups."
  [paths rollups]
  (for [p paths r rollups] [p r]))

(defn- get-thread-pool
  "Create a threadpool."
  [options]
  (cp/threadpool (:jobs options default-jobs)))

(defn- get-paths-from-paths-rollups
  "Get paths from a paths-rollups list."
  [rollup paths-rollups]
  (->> paths-rollups
       (filter #(= (second %) rollup))
       (map first)))

(defn- dry-mode-warn
  "Warn about dry mode."
  [options]
  (when-not (:run options false)
    (newline)
    (let [warn-str (str "DRY MODE IS ON! "
                        "To run in the normal mode use the '--run' option.")]
      (println warn-str)
      (log/warn warn-str))))

(defn- log-cli-cmd
  "Log a CLI command."
  [options]
  (when-let [raw-arguments (:raw-arguments options)]
    (log/info (str "Command line: "(str/join " " raw-arguments)))))

(defn- show-duration
  "Show duration."
  [interval]
  ;; https://stackoverflow.com/questions/3471397/pretty-print-duration-in-java
  (let [formatter (-> (PeriodFormatterBuilder.)
                      (.appendDays) (.appendSuffix "d ")
                      (.appendHours) (.appendSuffix "h ")
                      (.appendMinutes) (.appendSuffix "m ")
                      (.appendSeconds) (.appendSuffix "s")
                      (.toFormatter))
        duration-pp (.print formatter (.toPeriod interval))
        duration-sec (.getSeconds (.toStandardSeconds (.toDuration interval)))
        duration-str (format "Duration: %ss%s" duration-sec
                             (if (> duration-sec 59)
                               (format " (%s)" duration-pp) ""))]
    (log/info duration-str)
    (newline)
    (println duration-str)))

(defmacro with-duration
  "Show duration macro."
  [& body]
  `(let [start-time# (time/now)]
     ~@body
     (show-duration (time/interval start-time# (time/now)))))

(defn- show-stats
  "Show stats."
  [processed errors]
  (log/info (format "Stats: processed %s, errors: %s" processed errors))
  (newline)
  (println "Stats:")
  (println "  Processed: " processed)
  (println "  Errors:    " errors))

(defmacro generate-futures
  "Generate futures."
  [pool body coll]
  `(doall (map #(cp/future ~pool ~body) ~coll)))

(defn- process-futures
  "Process futures."
  [futures]
  (map #(let [result (deref %)]
          (when-not @clog/print-log?
            (prog/tick))
          result)
       futures))

(defn- process-metric
  "Process a metric."
  [processor mstore options tenant from to rollup-def path]
  (try
    (let [rollup (first rollup-def)
          period (last rollup-def)]
      (mp-process processor rollup period path from to))
    (catch Exception e
      (clog/error (str "Metric processing error: " e ", "
                       "path: " path ", "
                       "rollup: " rollup-def) e))))

(defn- process-metrics
  "Process metrics."
  [tenant rollups paths cass-hosts pstore options processor-fn tpool]
  (let [mstore (mstore/cassandra-metric-store cass-hosts options)
        processor (processor-fn mstore pstore tpool tenant rollups paths options)
        paths-rollups (mp-get-paths-rollups processor)]
    (try
      (let [from (:from options)
            to (:to options)
            proc-fn (partial process-metric processor mstore options tenant
                             from to)
            title (mp-get-title processor)]
        (prog/set-progress-bar!
         "[:bar] :percent :done/:total Elapsed :elapseds ETA :etas")
        (prog/config-progress-bar! :width pbar-width)
        (newline)
        (clog/info (str title ":"))
        (when-not @clog/print-log?
          (println title)
          (prog/init (count paths-rollups)))
        (let [futures (doall (map #(cp/future tpool (proc-fn (second %)
                                                             (first %)))
                                  paths-rollups))]
          (dorun (process-futures futures)))
        (when-not @clog/print-log?
          (prog/done)))
      (finally
        (mstore/shutdown mstore)
        (mp-show-stats processor (+ (:errors (mstore/get-stats mstore))
                                    (:errors (pstore/get-stats pstore))))))
    paths-rollups))

(defn- get-times
  "Get a list of times."
  [mstore options tenant rollup period path from to]
  (if (or from to)
    (let [result (mstore/fetch mstore tenant rollup period path from to nil)]
      (if (= result :mstore-error) [] (map #(:time %) result)))
    nil))

(defn- remove-metrics-processor
  "Metrics removal processor."
  [mstore pstore tpool tenant rollups paths options]
  (let [stats-processed (atom 0)]
    (reify MetricProcessor
      (mp-get-title [this]
        "Removing metrics")
      (mp-get-paths [this]
        (get-paths pstore tenant paths (:exclude-paths options)
                   (:sort options)))
      (mp-get-paths-rollups [this]
        (combine-paths-rollups (mp-get-paths this) rollups))
      (mp-process [this rollup period path from to]
        (swap! stats-processed inc)
        (let [times (get-times mstore options tenant rollup period path from to)]
          (clog/info (str "Removing metrics: "
                          "rollup: " rollup ", "
                          "period: " period ", "
                          "path: " path))
          (if times
            (when (seq times)
              (mstore/delete-times mstore tenant rollup period path times))
            (mstore/delete mstore tenant rollup period path))))
      (mp-show-stats [this errors]
        (show-stats @stats-processed errors)))))

(defn remove-metrics
  "Remove metrics."
  [tenant rollups paths cass-hosts es-url options]
  (try
    (with-duration
      (clog/set-logging! options)
      (log/info starting-str)
      (log-cli-cmd options)
      (dry-mode-warn options)
      (cp/with-shutdown! [tpool (get-thread-pool options)]
        (process-metrics tenant rollups paths cass-hosts
                         (pstore/elasticsearch-path-store es-url options)
                         options remove-metrics-processor tpool)))
    (catch Exception e
      (clog/unhandled-error e))))

(defn- list-metrics-processor
  "Metrics listing processor."
  [mstore pstore tpool tenant rollups paths options]
  (reify MetricProcessor
    (mp-get-title [this]
      "Metrics")
    (mp-get-paths [this]
      (get-paths pstore tenant paths (:exclude-paths options)
                 (:sort options)))
    (mp-get-paths-rollups [this]
      (combine-paths-rollups (mp-get-paths this) rollups))
    (mp-process [this rollup period path from to]
      (let [result (mstore/fetch mstore tenant rollup period path from to nil)]
        (dorun (map #(println (format list-metrics-str path rollup period
                                      (:time %) (:data %))) result))))
    (mp-show-stats [this errors])))

(defn list-metrics
  "List metrics."
  [tenant rollups paths cass-hosts es-url options]
  (try
    (clog/disable-logging!)
    (set-inspecting-on!)
    (cp/with-shutdown! [tpool (get-thread-pool options)]
      (process-metrics tenant rollups paths cass-hosts
                       (pstore/elasticsearch-path-store es-url options) options
                       list-metrics-processor tpool))
    (catch Exception e
      (clog/unhandled-error e))))

(defn- process-path
  "Process a path."
  [processor path]
  (try
    (pp-process processor path)
    (catch Exception e
      (clog/error (str "Path processing error: " e ", "
                       "path: " path) e))))

(defn- process-paths
  "Process paths."
  [tenant paths pstore options processor-fn tpool]
  (let [processor (processor-fn pstore tenant options)
        title (pp-get-title processor)
        proc-fn (partial process-path processor)]
    (try
      (prog/set-progress-bar!
       "[:bar] :percent :done/:total Elapsed :elapseds ETA :etas")
      (prog/config-progress-bar! :width pbar-width)
      (newline)
      (clog/info (str title ":"))
      (when-not @clog/print-log?
        (println title)
        (prog/init (count paths)))
      (let [futures (doall (map #(cp/future tpool (proc-fn %)) paths))]
        (dorun (process-futures futures)))
      (when-not @clog/print-log?
        (prog/done))
      (finally
        (pp-show-stats processor (:errors (pstore/get-stats pstore)))))))

(defn- remove-paths-processor
  "Path removal processor."
  [pstore tenant options]
  (let [stats-processed (atom 0)]
    (reify PathProcessor
      (pp-get-title [this]
        "Removing paths")
      (pp-process [this path]
        (swap! stats-processed inc)
        (clog/info (str "Removing path: " path))
        (pstore/delete-query pstore tenant false false path))
      (pp-show-stats [this errors]
        (show-stats @stats-processed errors)))))

(defn remove-paths
  "Remove paths."
  [tenant paths es-url options]
  (try
    (with-duration
      (clog/set-logging! options)
      (log/info starting-str)
      (log-cli-cmd options)
      (dry-mode-warn options)
      (cp/with-shutdown! [tpool (get-thread-pool options)]
        (process-paths tenant paths
                       (pstore/elasticsearch-path-store es-url options)
                       options remove-paths-processor tpool)))
    (catch Exception e
      (clog/unhandled-error e))))

(defn- list-paths-processor
  "Paths listing processor."
  [pstore tenant options]
  (reify PathProcessor
    (pp-get-title [this]
      "Paths")
    (pp-process [this path]
      (dorun (map println (lookup-paths pstore tenant false false [path]
                                        (:exclude-paths options)
                                        (:sort options)))))
    (pp-show-stats [this errors])))

(defn list-paths
  "List paths."
  [tenant paths es-url options]
  (try
    (clog/disable-logging!)
    (set-inspecting-on!)
    (cp/with-shutdown! [tpool (get-thread-pool options)]
      (process-paths tenant paths (pstore/elasticsearch-path-store es-url options)
                     options list-paths-processor tpool))
    (catch Exception e
      (clog/unhandled-error e))))

(defn- check-metric-obsolete
  "Check a metric for obsolescence."
  [mstore tenant from path rollup-def]
  (try
    (let [rollup (first rollup-def)
          period (last rollup-def)]
      (when-not @inspecting?
        (clog/info (str "Checking metrics: "
                        "path: " path ", "
                        "rollup: " rollup ", "
                        "period: " period)))
      (let [data (mstore/fetch mstore tenant rollup period path from nil 1)]
        (if (and (not= data :mstore-error) (not (seq data)))
          (do
            (log/debug (str "Metrics on path '" path "' are obsolete"))
            [path rollup-def])
          nil)))
    (catch Exception e
      (clog/error (str "Metric obsolescence checking error: " e ", "
                       "path: " path ", "
                       "rollup: " rollup-def) e))))

(defn- filter-obsolete-metrics
  "Filter obsolete metrics."
  [mstore tpool tenant rollups options paths]
  (let [threshold (:threshold options default-obsolete-metrics-threshold)
        from (timec/to-epoch (time/minus (time/now) (time/seconds threshold)))
        title "Checking metrics"
        rollup (first rollups)
        paths-rollups (combine-paths-rollups paths [rollup])
        sort (get-sort-or-dummy-fn (:sort options))]
    (prog/set-progress-bar!
     "[:bar] :percent :done/:total Elapsed :elapseds ETA :etas")
    (prog/config-progress-bar! :width pbar-width)
    (newline)
    (clog/info (str title (if @inspecting? "..." ":")))
    (when-not @inspecting?
      (clog/info (str "Threshold: " threshold ", "
                      "from: " from " ("
                      (timef/unparse (:rfc822 timef/formatters)
                                     (timec/from-long (* from 1000))) ")")))
    (when-not @clog/print-log?
      (println title)
      (prog/init (count paths-rollups)))
    (let [futures (doall
                   (map #(cp/future tpool (check-metric-obsolete mstore tenant
                                                                 from
                                                                 (first %)
                                                                 (second %)))
                        paths-rollups))
          obsolete-paths (->> futures
                              (process-futures)
                              (remove nil?)
                              (get-paths-from-paths-rollups rollup)
                              (sort))
          _ (clog/info (str "Found obsolete metrics on "
                            (count obsolete-paths) " paths"))
          obsolete (combine-paths-rollups obsolete-paths rollups)]
      (when-not @clog/print-log?
        (prog/done))
      obsolete)))

(defn- collect-path-info
  "Add a path to the paths info database."
  [path]
  (when (:leaf path)
    (swap! paths-info conj (:path path))))

(defn- remove-obsolete-metrics-processor
  "Obsolete metrics removal processor."
  [mstore pstore tpool tenant rollups paths options]
  (let [stats-processed (atom 0)]
    (reify MetricProcessor
      (mp-get-title [this]
        "Removing obsolete metrics")
      (mp-get-paths [this]
        (swap! paths-info (fn [_] #{}))
        (get-paths pstore tenant paths (:exclude-paths options) (:sort options)
                   collect-path-info))
      (mp-get-paths-rollups [this]
        (filter-obsolete-metrics mstore tpool tenant rollups options
                                 (mp-get-paths this)))
      (mp-process [this rollup period path from to]
        (swap! stats-processed inc)
        (clog/info (str "Removing obsolete metrics: "
                        "rollup: " rollup ", "
                        "period: " period ", "
                        "path: " path))
        (mstore/delete mstore tenant rollup period path))
      (mp-show-stats [this errors]
        (show-stats @stats-processed errors)))))

(defn- remove-obsolete-paths-processor
  "Obsolete path removal processor."
  [pstore tenant options]
  (let [stats-processed (atom 0)]
    (reify PathProcessor
      (pp-get-title [this]
        "Removing obsolete paths")
      (pp-process [this path]
        (swap! stats-processed inc)
        (clog/info (str "Removing obsolete path: " path))
        (pstore/delete pstore tenant path))
      (pp-show-stats [this errors]
        (show-stats @stats-processed errors)))))

(defn remove-obsolete-data
  "Remove obsolete data."
  [tenant rollups paths cass-hosts es-url options]
  (try
    (with-duration
      (clog/set-logging! options)
      (log/info starting-str)
      (log-cli-cmd options)
      (dry-mode-warn options)
      (cp/with-shutdown! [tpool (get-thread-pool options)]
        (let [pstore (pstore/elasticsearch-path-store es-url options)
              sort (get-sort-or-dummy-fn (:sort options))
              processed-data (process-metrics tenant rollups paths cass-hosts
                                              pstore options
                                              remove-obsolete-metrics-processor
                                              tpool)
              obsolete-paths (->> processed-data
                                  (get-paths-from-paths-rollups (first rollups))
                                  (filter #(contains? @paths-info %))
                                  (sort)
                                  (doall))]
          (swap! paths-info (fn [_] nil))
          (process-paths tenant obsolete-paths pstore options
                         remove-obsolete-paths-processor tpool))))
    (catch Exception e
      (clog/unhandled-error e))))

(defn list-obsolete-data
  "List obsolete data."
  [tenant rollups paths cass-hosts es-url options]
  (try
    (clog/disable-logging!)
    (set-inspecting-on!)
    (cp/with-shutdown! [tpool (get-thread-pool options)]
      (let [mstore (mstore/cassandra-metric-store cass-hosts options)
            pstore (pstore/elasticsearch-path-store es-url options)
            sort (get-sort-or-dummy-fn (:sort options))
            obsolete-data (->> (get-paths pstore tenant paths
                                        (:exclude-paths options)
                                        (:sort options))
                             (filter-obsolete-metrics mstore tpool tenant
                                                      rollups options)
                             (get-paths-from-paths-rollups (first rollups))
                             (sort))]
      (newline)
      (dorun (map println obsolete-data))))
    (catch Exception e
      (clog/unhandled-error e))))

(defn- path-str2list
  "Convert a string path to its list representation."
  [path]
  (str/split path #"\."))

(defn- path-list2str
  "Convert a list path to its string representation."
  [path]
  (str/join "." path))

(defn- add-path-to-tree
  "Add a path to the paths tree."
  [tree-impl path]
  (let [lpath (path-str2list (:path path))
        leaf? (:leaf path)]
    (t-add-path tree-impl lpath leaf?)))

(defn- hm-tree
  "Hash-map tree implementation."
  []
  (let [tree (atom {})]
    (reify Tree
      (t-add-path [this path leaf?]
        (if leaf?
          (let [path (conj (vec (butlast path)) :leafs)]
            (swap! tree #(assoc-in % path (inc (get-in % path 0)))))
          (swap! tree #(assoc-in % path (get-in % path {})))))
      (t-get [this path]
        (if (t-path-leaf? this path)
          (get-in @tree path)
          (if (empty? path)
            (map vector (keys @tree))
            (let [path (vec path)]
              (map #(conj path %) (keys (get-in @tree path)))))))
      (t-path-leaf? [this path]
        (let [val (get-in @tree path)]
          (and (not (instance? clojure.lang.PersistentHashMap val))
               (not (instance? clojure.lang.PersistentArrayMap val)))))
      (t-path-empty? [this path]
        (= (get-in @tree path) {}))
      (t-delete-path [this path]
        (swap! tree utils/dissoc-in path))
      (t-get-raw-tree [this]
        @tree))))

(defn- empty-paths-finder
  "Empty paths finder."
  [tree-impl pstore tpool tenant paths options]
  (let [empty-paths (atom [])
        processed (atom 0)
        non-leafs (atom 0)]
    (reify TreeProcessor
      (tp-get-title [this]
        "Checking paths")
      (tp-get-paths [this]
        (get-paths pstore tenant paths nil false
                   (partial add-path-to-tree tree-impl)))
      (tp-process-path [this path]
        ;; Leaf?
        (if (t-path-leaf? tree-impl path)
          ;; Leaf
          (let [leafs (t-get tree-impl path)]
            (swap! processed + leafs)
            (when-not @clog/print-log?
              (prog/tick-by leafs)))
          ;; Non-leaf
          (let [spath (path-list2str path)]
            (when (seq path)
              (swap! processed inc)
              (swap! non-leafs inc)
              (when-not @inspecting?
                (clog/info (str "Checking path: " spath))))
            (when (t-path-empty? tree-impl path)
              (log/debug (str "Path '" spath "' is empty"))
              (t-delete-path tree-impl path)
              (swap! empty-paths conj spath))
            (when (and (not @clog/print-log?) (seq path))
              (prog/tick)))))
      (tp-get-data [this]
        @empty-paths)
      (tp-show-stats [this]
        (let [empty-count (count @empty-paths)]
          (log/info (format "Stats: processed %s, non-leafs: %s, empty: %s"
                            @processed @non-leafs empty-count))
          (newline)
          (println "Stats:")
          (println "  Processed: " @processed)
          (println "  Non-leafs: " @non-leafs)
          (println "  Empty:     " empty-count))))))

(defn- tree-walker
  "Tree walker."
  [tenant paths pstore options tree-processor-fn tpool]
  (let [tree-impl (hm-tree)
        processor (tree-processor-fn tree-impl pstore tpool tenant paths options)
        title (tp-get-title processor)
        paths-count (doall (tp-get-paths processor))]
    (letfn [(walk [path]
              (try
                (when-not (t-path-leaf? tree-impl path)
                  (dorun (map walk (t-get tree-impl path))))
                (when (seq path)
                  (tp-process-path processor path))
                (clog/error (str "Error in tree walker: " e ", "
                                 "path: " path) e)))]
      (try
        (prog/set-progress-bar!
         "[:bar] :percent :done/:total Elapsed :elapseds ETA :etas")
        (prog/config-progress-bar! :width pbar-width)
        (newline)
        (clog/info (str title (if @inspecting? "..." ":")))
        (when-not @clog/print-log?
          (println title)
          (prog/init paths-count))
        (dorun (cp/upmap tpool walk (t-get tree-impl [])))
        (when-not @clog/print-log?
          (prog/done))
        (let [empty-paths (tp-get-data processor)]
          (when @inspecting?
            (clog/info (str "Found " (count empty-paths) " empty paths")))
          empty-paths)
        (finally
          (when-not @inspecting?
            (tp-show-stats processor)))))))

(defn- remove-empty-paths-processor
  "Empty path removal processor."
  [pstore tenant options]
  (let [stats-processed (atom 0)]
    (reify PathProcessor
      (pp-get-title [this]
        "Removing empty paths")
      (pp-process [this path]
        (swap! stats-processed inc)
        (clog/info (str "Removing empty path: " path))
        (pstore/delete pstore tenant path))
      (pp-show-stats [this errors]
        (show-stats @stats-processed errors)))))

(defn remove-empty-paths
  "Remove empty paths."
  [tenant paths es-url options]
  (try
    (with-duration
      (clog/set-logging! options)
      (log/info starting-str)
      (log-cli-cmd options)
      (dry-mode-warn options)
      (cp/with-shutdown! [tpool (get-thread-pool options)]
        (let [pstore (pstore/elasticsearch-path-store es-url options)
              sort (get-sort-or-dummy-fn (:sort options))
              empty-paths (->> (tree-walker tenant paths pstore options
                                            empty-paths-finder tpool)
                               (sort))]
          (process-paths tenant empty-paths pstore options
                         remove-empty-paths-processor tpool))))
    (catch Exception e
      (clog/unhandled-error e))))

(defn list-empty-paths
  "List empty paths."
  [tenant paths es-url options]
  (try
    (clog/disable-logging!)
    (set-inspecting-on!)
    (cp/with-shutdown! [tpool (get-thread-pool options)]
      (let [pstore (pstore/elasticsearch-path-store es-url options)
            sort (get-sort-or-dummy-fn (:sort options))
            empty-paths (->> (tree-walker tenant paths pstore options
                                          empty-paths-finder tpool)
                             (sort))]
        (newline)
        (dorun (map println empty-paths))))
    (catch Exception e
      (clog/unhandled-error e))))
