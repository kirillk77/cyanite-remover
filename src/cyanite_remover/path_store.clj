(ns cyanite-remover.path-store
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esri]
            [clojurewerkz.elastisch.rest.document :as esrd]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cyanite-remover.logging :as clog]
            [cyanite-remover.utils :as utils]))

(defprotocol PathStore
  "Path store."
  (lookup [this tenant leafs-only limit-depth path exclude-paths])
  (delete [this tenant path])
  (delete-query [this tenant leafs-only limit-depth path])
  (get-stats [this]))

(def ^:const default-es-index "cyanite_paths")
(def ^:const default-es-scroll-batch-size 100000)

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
      (str/replace #"\.|\*|\?" {"." "\\." "*" ".*" "?" ".?"})
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

(defn- join-wildcards [paths]
  (let [wrap-to-brackets #(str "(" % ")")]
    (->> paths
         (map wildcards-to-regexp)
         (map wrap-to-brackets)
         (str/join "|")
         wrap-to-brackets)))

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
  [search-fn scroll-fn tenant leafs-only limit-depth path batch-size batch-rate]
  (let [throttle-fn (utils/fn-or-trtlfn identity batch-rate)
        query (build-query (build-filter tenant leafs-only limit-depth path))
        _ (log/trace (str "ES search query: " query))
        resp (search-fn :query query :size batch-size
                        :search_type "query_then_fetch" :scroll "1m")]
    (log/trace (str "ES search response: " resp))
    (map :_source (map throttle-fn (scroll-fn resp)))))

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
  (log/trace (str "ES delete query response: " response))
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
         (pos? failed) (do (log-shards-errors failed (:failures shards) path
                                              stats-errors) false))
        true)
      (error-fn "shards"))))

(defn- get-doc-id
  "Get a document ID."
  [tenant path]
  (str tenant "_" path))

(defn- delete-impl
  "Delete implementation."
  [conn index es-def-type run stats-processed stats-errors tenant path]
  (try
    (swap! stats-processed inc)
    (let [id (get-doc-id tenant path)]
      (log/trace (str "ES delete document ID: " id))
      (when run
        (let [response (esrd/delete conn index es-def-type id)]
          (log/trace (str "ES delete response: " response)))))
    (catch Exception e
      (log-error e path stats-errors))))

(defn- deleteq-impl
  "Delete query implementation."
  [conn index es-def-type run stats-processed stats-errors tenant leafs-only
   limit-depth path]
  (try
    (swap! stats-processed inc)
    (let [query (build-query (build-filter tenant leafs-only
                                           limit-depth path))]
      (log/trace (str "ES delete query: " query))
      (when run
        (let [response (esrd/delete-by-query conn index es-def-type query)]
          (process-response-delete response index path stats-errors))))
    (catch Exception e
      (log-error e path stats-errors))))

(defn elasticsearch-path-store
  "Create an Elasticsearch path store."
  [url options]
  (log/info "Creating the path store...")
  (let [run (:run options false)
        index (:elasticsearch-index options default-es-index)
        scroll-batch-size (:elasticsearch-scroll-batch-size
                           options default-es-scroll-batch-size)
        scroll-batch-rate (:elasticsearch-scroll-batch-rate options)
        delete-request-rate (:elasticsearch-delete-request-rate options)
        conn (esr/connect url)
        search-fn (partial esrd/search conn index es-def-type)
        scroll-fn (partial esrd/scroll-seq conn)
        data-stored? (atom false)
        stats-processed (atom 0)
        stats-errors (atom 0)
        delete-impl (partial delete-impl conn index es-def-type run
                             stats-processed stats-errors)
        deleteq-impl (partial deleteq-impl conn index es-def-type run
                              stats-processed stats-errors)
        delete-fn (utils/fn-or-trtlfn delete-impl delete-request-rate)
        deleteq-fn (utils/fn-or-trtlfn deleteq-impl delete-request-rate)]
    (log/info (str "The path store has been created. "
                   "URL: " url
                   ", index: " index
                   ", scroll batch size: " scroll-batch-size
                   (utils/string-or-empty scroll-batch-rate
                                          (str ", scroll batch rate: "
                                               scroll-batch-rate))
                   (utils/string-or-empty delete-request-rate
                                          (str ", delete request rate: "
                                               delete-request-rate))))
    (reify
      PathStore
      (lookup [this tenant leafs-only limit-depth path exclude-paths]
        (try
          (let [paths (search search-fn scroll-fn tenant leafs-only
                              limit-depth path scroll-batch-size
                              scroll-batch-rate)
                re-excludes (re-pattern (join-wildcards exclude-paths))]
            (if-not exclude-paths
              paths
              (remove #(re-matches re-excludes (:path %)) paths)))
          (catch Exception e
            (log-error e path stats-errors))))
      (delete [this tenant path]
        (delete-fn tenant path))
      (delete-query [this tenant leafs-only limit-depth path]
        (deleteq-fn tenant leafs-only limit-depth path))
      (get-stats [this]
        {:processed @stats-processed
         :errors @stats-errors}))))
