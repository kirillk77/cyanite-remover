(ns cyanite-remover.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clj-progress.core :as prog]
            [clojure.tools.logging :as log]
            [cyanite-remover.logging :as wlog]
            [cyanite-remover.metric-store :as mstore]
            [cyanite-remover.path-store :as pstore]
            [com.climate.claypoole :as cp]))

(def ^:const default-jobs 1)

(def ^:const pbar-width 35)

(def stats-processed (atom 0))

(def list-metrics-str "Path: %s, rollup: %s, period: %s, time: %s, data: %s")
(def starting-str "==================== Starting ====================")

(defn- lookup-paths
  "Lookup paths."
  [pstore tenant leafs-only limit-depth paths]
  (let [lookup-fn (partial pstore/lookup pstore tenant leafs-only limit-depth)]
    (sort (flatten (map lookup-fn paths)))))

(defn- get-paths
  "Get paths."
  [pstore tenant paths-to-lookup]
  (let [paths (lookup-paths pstore tenant true false paths-to-lookup)
        title "Getting paths"]
    (newline)
    (wlog/info (str title "..."))
    (when-not @wlog/print-log?
      (println title)
      (prog/set-progress-bar! "[:bar] :done")
      (prog/config-progress-bar! :width pbar-width)
      (prog/init 0)
      (doseq [_ paths]
        (prog/tick))
      (prog/done))
    (wlog/info (format "Found %s paths" (count paths)))
    paths))

(defn- dry-mode-warn
  "Warn about dry mode."
  [options]
  (when-not (:run options false)
    (newline)
    (let [warn-str (str "DRY MODE IS ON! "
                        "To run in the normal mode use the '--run' option.")]
      (println warn-str)
      (log/warn warn-str))))

(defn- show-stats
  "Show stats."
  [processed errors]
  (log/info (format "Stats: processed %s, errors: %s" processed errors))
  (newline)
  (println "Stats:")
  (println "  Processed: " processed)
  (println "  Errors:    " errors))

(defn- process-metric
  "Process a metric."
  [process-fn mstore options tenant from to rollup-def path]
  (try
    (let [rollup (first rollup-def)
          period (last rollup-def)]
      (process-fn mstore options tenant rollup period path from to))
    (catch Exception e
      (wlog/error (str "Metric processing error: " e ", "
                       "path: " path) e))))

(defn- process-metrics
  "Process metrics."
  [tenant rollups paths cass-hosts es-url options process-fn title show-stats?]
  (let [mstore (mstore/cassandra-metric-store cass-hosts options)
        pstore (pstore/elasticsearch-path-store es-url options)]
    (try
      (let [all-paths (get-paths pstore tenant paths)
            from (:from options)
            to (:to options)
            jobs (:jobs options default-jobs)
            tpool (cp/threadpool jobs)
            proc-fn (partial process-metric process-fn mstore options tenant
                             from to)]
        (prog/set-progress-bar!
         "[:bar] :percent :done/:total Elapsed :elapseds ETA :etas")
        (prog/config-progress-bar! :width pbar-width)
        (newline)
        (wlog/info (str title ":"))
        (when-not @wlog/print-log?
          (println title)
          (prog/init (* (count all-paths) (count rollups))))
        (let [paths-rollups (for [p all-paths r rollups] [p r])
              futures (doall (map #(cp/future tpool
                                              (proc-fn (second %) (first %)))
                                  paths-rollups))]
          (dorun (map #(do (deref %) (when-not @wlog/print-log? (prog/tick)))
                      futures)))
        (when-not @wlog/print-log?
          (prog/done)))
      (finally
        (mstore/shutdown mstore)
        (when show-stats?
          (let [mstore-stats (mstore/get-stats mstore)
                pstore-stats (pstore/get-stats pstore)]
            (show-stats @stats-processed
                        (+ (:errors mstore-stats)
                           (:errors pstore-stats)))))))))

(defn- get-times
  "Get a list of times."
  [mstore options tenant rollup period path from to]
  (if (or from to)
    (let [result (mstore/fetch mstore tenant rollup period path from to)]
      (if (= result :mstore-error) [] (map #(:time %) result)))
    nil))

(defn- remove-metrics-path
  "List metrics for a path."
  [mstore options tenant rollup period path from to]
  (swap! stats-processed inc)
  (let [times (get-times mstore options tenant rollup period path from to)]
    (wlog/info (str "Removing metrics: "
                    "rollup: " rollup ", "
                    "period: " period ", "
                    "path: " path))
    (if times
      (when (seq times)
        (mstore/delete-times mstore tenant rollup period path times))
      (mstore/delete mstore tenant rollup period path))))

(defn remove-metrics
  "Remove metrics."
  [tenant rollups paths cass-hosts es-url options]
  (try
    (wlog/set-logging! options)
    (log/info starting-str)
    (dry-mode-warn options)
    (process-metrics tenant rollups paths cass-hosts es-url options
                     remove-metrics-path "Removing metrics" true)
    (catch Exception e
      (wlog/unhandled-error e))))

(defn- list-metrics-path
  "List metrics for a path."
  [mstore options tenant rollup period path from to]
  (let [result (mstore/fetch mstore tenant rollup period path from to)]
    (dorun (map #(println (format list-metrics-str path rollup period (:time %)
                                  (:data %))) result))))

(defn list-metrics
  "List metrics."
  [tenant rollups paths cass-hosts es-url options]
  (try
    (wlog/disable-logging!)
    (process-metrics tenant rollups paths cass-hosts es-url options
                     list-metrics-path "Metrics" false)
    (catch Exception e
      (wlog/unhandled-error e))))

(defn- process-paths
  "Process paths."
  [tenant paths es-url options process-fn title show-stats?]
  (wlog/info (str title ":"))
  (let [pstore (pstore/elasticsearch-path-store es-url options)]
    (process-fn pstore options tenant paths)
    (when show-stats?
      (let [pstore-stats (pstore/get-stats pstore)]
        (show-stats (:processed pstore-stats) (:errors pstore-stats))))))

(defn remove-paths
  "Remove paths."
  [tenant paths es-url options]
  (try
    (wlog/set-logging! options)
    (log/info starting-str)
    (dry-mode-warn options)
    (process-paths tenant paths es-url options
                   (fn [pstore options tenant paths]
                     (swap! stats-processed inc)
                     (pstore/delete pstore tenant false false paths))
                   "Removing paths" true)
    (catch Exception e
      (wlog/unhandled-error e))))

(defn list-paths
  "List paths."
  [tenant paths es-url options]
  (try
    (wlog/disable-logging!)
    (process-paths tenant paths es-url options
                   (fn [pstore options tenant paths]
                     (dorun (map println (lookup-paths pstore tenant false
                                                       false paths))))
                   "Paths" false)
    (catch Exception e
      (wlog/unhandled-error e))))
