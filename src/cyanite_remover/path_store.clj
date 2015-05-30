(ns cyanite-remover.path-store
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esri]
            [clojurewerkz.elastisch.rest.document :as esrd]
            [clojurewerkz.elastisch.rest.response :as esrr]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cyanite-remover.logging :as clog]))

(defprotocol PathStore
  "Path store."
  (lookup [this tenant leafs-only limit-depth path])
  (delete [this tenant leafs-only limit-depth path])
  (get-stats [this]))

(def ^:const default-es-index "cyanite_paths")
(def ^:const es-def-type "path")

(def ^:const es-type-map
  {es-def-type {:_all { :enabled false }
                :_source { :compress false }
                :properties {:tenant {:type "string" :index "not_analyzed"}
                             :path {:type "string" :index "not_analyzed"}}}})

(defn- log-error
  "Log a error."
  [error path stats-errors]
  (clog/error (format "Path store error: path: %s, error: %s" path error) error)
  (swap! stats-errors inc))

(defn- wildcards-to-regexp
  "Convert Graphite wildcards to a regular expression."
  [path]
  (-> path
      ;; Wildcards
      (str/replace #"\.|\*" {"." "\\." "*" ".*" })
      ;; Lists
      (str/replace #"\{|\}|," {"{" "(" "}" ")" "," "|"})
      ;; Ranges
      (str/replace #"\[(\d+)-(\d+)\]"
                   (fn [[_ r1s r2s]]
                     (let [r1i (Integer/parseInt r1s)
                           r2i (Integer/parseInt r2s)
                           r1 (apply min [r1i r2i])
                           r2 (apply max [r1i r2i])]
                       (format "(%s)" (str/join "|" (range r1 (inc r2)))))))))

(defn- add-depth-filter
  "Add a depth filter."
  [filter limit-depth path]
  (if limit-depth
    (let [depth (inc (count (re-seq (re-pattern #"\.") path)))]
      (conj filter {:range {:depth {:from depth :to depth}}}))
    filter))

(defn- add-leaf-filter
  "Add a leaf filter."
  [filter leafs-only path]
  (if leafs-only (conj filter {:term {:leaf true}}) filter))

(defn- build-filter
  "Build an Elasticsearch filter."
  [tenant leafs-only limit-depth path]
  (let [filter (vector
                {:term {:tenant tenant}}
                {:regexp {:path (wildcards-to-regexp path) :_cache true}})]
    (-> filter
        (add-depth-filter limit-depth path)
        (add-leaf-filter leafs-only path))))

(defn- build-query
  "Build an Elasticsearch query."
  [filter]
  {:filtered {:filter {:bool {:must filter}}}})

(defn- search
  "Search for a path."
  [search-fn scroll-fn tenant leafs-only limit-depth path]
  (let [query (build-query (build-filter tenant leafs-only limit-depth path))
        _ (log/trace (str "ES search query: " query))
        resp (search-fn :query query :size 100 :search_type "query_then_fetch"
                       :scroll "1m")]
    (log/trace (str "ES search respond: " resp))
    (map #(:_source %) (scroll-fn resp))))

(defn- log-shards-errors
  "Log shards' errors."
  [failed failures path stats-errors]
  (let [shards-errors (str/join "\n" (map #(format "shard: %s, error: %s"
                                                   (:shard %) (:reason %))
                                          failures))]
    (log-error
     (format "there were %s errors during the removal of the path:\n%s"
             failed shards-errors) path stats-errors)))

(defn- process-response-delete
  "Process response of delete-by-query."
  [response index path stats-errors]
  (log/trace (str "ES delete respond: " response))
  (let [error-fn #(do (log-error (format "Can't get \"%s\" value from response" %)
                                 path stats-errors) false)]
    (if-let [shards (:_shards (get (:_indices response) (keyword index)))]
      (let [total (:total shards)
            successful (:successful shards)
            failed (:failed shards)]
        (cond
         (not total) (error-fn "total")
         (not successful) (error-fn "successful")
         (not failed) (error-fn "failed")
         (> failed 0) (do (log-shards-errors failed (:failures shards) path
                                             stats-errors) false))
        true)
      (error-fn "shards"))))

(defn elasticsearch-path-store
  "Create an Elasticsearch path store."
  [url options]
  (log/info "Creating the path store...")
  (let [run (:run options false)
        index (:elasticsearch-index options default-es-index)
        conn (esr/connect url)
        search-fn (partial esrd/search conn index es-def-type)
        scroll-fn (partial esrd/scroll-seq conn)
        delete-fn (partial esrd/delete-by-query conn index es-def-type)
        data-stored? (atom false)
        stats-processed (atom 0)
        stats-errors (atom 0)]
    (log/info (str "The path store has been created. "
                   "URL: " url ", "
                   "index: " index))
    (reify
      PathStore
      (lookup [this tenant leafs-only limit-depth path]
        (try
          (map :path (search search-fn scroll-fn tenant leafs-only limit-depth
                             path))
          (catch Exception e
            (log-error e path stats-errors))))
      (delete [this tenant leafs-only limit-depth path]
        (try
          (swap! stats-processed inc)
          (let [query (build-query (build-filter tenant leafs-only
                                                 limit-depth path))]
            (log/trace (str "ES delete query: " query))
            (if run
              (let [response (delete-fn query)]
                (process-response-delete response index path stats-errors))))
          (catch Exception e
            (log-error e path stats-errors))))
      (get-stats [this]
        {:processed @stats-processed
         :errors @stats-errors}))))
