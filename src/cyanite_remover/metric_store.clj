(ns cyanite-remover.metric-store
  (:require [qbits.alia :as alia]
            [qbits.alia.policy.load-balancing :as alia_lbp]
            [clojure.core.async :as async]
            [throttler.core :as trtl]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cyanite-remover.logging :as wlog])
  (:import [com.datastax.driver.core
            PreparedStatement
            BatchStatement]))

(defprotocol MetricStore
  (fetch [this tenant rollup period path from to])
  (delete [this tenant rollup period path])
  (delete-times [this tenant rollup period path times])
  (get-stats [this])
  (shutdown [this]))

(def ^:const default-cassandra-keyspace "metric")
(def ^:const default-cassandra-channel-size 10000)
(def ^:const default-cassandra-batch-size 1000)
(def ^:const default-cassandra-batch-rate nil)
(def ^:const default-cassandra-options {})

(defmacro go-while
  [test body]
  `(async/go
     (while ~test
       ~body)))

(def ^:const fetch-cql
  (str "SELECT data, time FROM metric WHERE "
       "tenant = ? AND rollup = ? AND period = ? AND path = ? %s "
       "ORDER BY time"))

(def ^:const delete-cql
  (str "DELETE FROM metric WHERE "
       "tenant = ? AND rollup = ? AND period = ? AND path = ?"))

(def ^:const fetch-cqls
  (let [comb [[false false]
              [true false]
              [false true]
              [true true]]]
    (->> comb
         (map #(format fetch-cql (str (if (first %) " AND time >= ?" "")
                                      (if (last %) " AND time <= ?" ""))))
         (zipmap comb))))

(def ^:const delete-cqls {false delete-cql
                          true (str delete-cql " AND time = ?")})

(defn- prepare-cqls
  "Prepare a family of CQL queries."
  [cqls session]
  (reduce-kv #(assoc %1 %2 {:prepared (alia/prepare session %3) :cql %3})
             {} cqls))

(defn- get-prepared-cql
  "Get a prepared CQL query."
  [pcqls & args]
  (get pcqls (map #(not (nil? %)) args)))

(defn- build-values
  "Build list of values."
  [tenant rollup period path & args]
  (into [tenant (int rollup) (int period) path]
        (map long (remove nil? args))))

(defn- build-batch
  "Build a batch of prepared statements"
  [^PreparedStatement statement values]
  (let [batch-statement (BatchStatement.)]
    (let [{:keys [prepared cql]} statement]
      (doseq [value values]
        (.add batch-statement (.bind prepared (into-array Object value))))
      batch-statement)))

(defn- string-or-empty
  "Return a passed string if value is not nil and an empty string otherwise."
  [value string]
  (if value (str string value) ""))

(defn- log-error
  "Log a error."
  [error rollup period path stats-errors]
  (wlog/error (str "Metric store error: " error ", "
                   "rollup " rollup ", "
                   "period: " period ", "
                   "path: " path) error)
  (swap! stats-errors inc))

(defn- log-deletion
  "Log a deletion."
  [rollup period path & [times]]
  (log/debug (str "Removing metrics: "
                  (string-or-empty rollup "rollup: ")
                  (string-or-empty period ", period: ")
                  (string-or-empty path ", path: ")
                  (string-or-empty (vec times) ", time: "))))

(defn- get-delete-channel
  "Get delete channel."
  [session chan-size batch-rate data-processed? run stats-errors]
  (let [ch-in (async/chan chan-size)
        ch (if batch-rate (trtl/throttle-chan ch-in batch-rate :second) ch-in)
        pcqls (prepare-cqls delete-cqls session)]
    (go-while (not @data-processed?)
              (let [data (async/<! ch)]
                (if data
                  (let [{:keys [values rollup period path]} data]
                    (try
                      (let [has-time (> (count (first data)) 4)
                            statement (get pcqls has-time)
                            query (build-batch statement values)]
                        (when run
                          (async/take!
                           (alia/execute-chan session query
                                              {:consistency :any})
                           (fn [rows-or-e]
                             (if (instance? Throwable rows-or-e)
                               (log-error rows-or-e rollup period path
                                          stats-errors))))))
                      (catch Exception e
                        (log-error e rollup period path stats-errors))))
                  (when (not @data-processed?)
                    (swap! data-processed? (fn [_] true))))))
    ch-in))

(defn cassandra-metric-store
  "Cassandra metric store."
  [hosts options]
  (log/info "Creating the metric store...")
  (let [run (:run options false)
        keyspace (:cassandra-keyspace options default-cassandra-keyspace)
        c-options (merge {:contact-points hosts}
                         default-cassandra-options
                         (:cassandra-options options {}))
        _ (log/info "Cassandra options: " c-options)
        session (-> (alia/cluster c-options)
                    (alia/connect keyspace))
        fetch-pcqls (prepare-cqls fetch-cqls session)
        chan-size (:cassandra-channel-size options default-cassandra-channel-size)
        batch-size (:cassandra-batch-size options default-cassandra-batch-size)
        batch-rate (:cassandra-batch-rate options default-cassandra-batch-rate)
        delete-processed? (atom false)
        stats-processed (atom 0)
        stats-errors (atom 0)
        channel (get-delete-channel session chan-size batch-rate
                                    delete-processed? run stats-errors)
        batch (atom [])]
    (log/info (str "The metric store has been created. "
                   "Keyspace: " keyspace ", "
                   "channel size: " chan-size ", "
                   "batch size: " batch-size
                   "batch rate: " batch-rate))
    (reify
      MetricStore
      (fetch [this tenant rollup period path from to]
        (try
          (let [statement (get-prepared-cql fetch-pcqls from to)
                values (build-values tenant rollup period path from to)
                prepared (:prepared statement)
                cql (:cql statement)
                query (alia/bind prepared values)]
            (log/debug (str "Fetching metrics: "
                            "rollup: " rollup ", "
                            "period: " period ", "
                            "path: " path
                            (string-or-empty from ", from: ")
                            (string-or-empty to ", to: ")))
            (alia/execute session query))
          (catch Exception e
            (log-error e rollup period path stats-errors)
            :mstore-error)))
      (delete [this tenant rollup period path]
        (log-deletion rollup period path)
        (swap! stats-processed inc)
        (let [values (build-values tenant rollup period path)]
          (swap! batch
                 #(if (< (count %) batch-size)
                    (conj % values)
                    (do (async/>!! channel {:values (conj % values)}) [])))))
      (delete-times [this tenant rollup period path times]
        (log-deletion rollup period path times)
        (swap! stats-processed inc)
        (let [series (map #(build-values tenant rollup period path %) times)
              batches (partition-all batch-size series)]
          (dorun (map #(async/>!! channel {:values %
                                           :rollup rollup
                                           :period period
                                           :path path}) batches))))
      (get-stats [this]
        {:processed @stats-processed
         :errors @stats-errors})
      (shutdown [this]
        (log/info "Shutting down the metric store...")
        (swap! batch #(if (> (count %) 0)
                        (do (async/>!! channel {:values %}) [])
                        %))
        (async/close! channel)
        (while (and (> @stats-processed 0) (not @delete-processed?))
          (Thread/sleep 100))
        (.close session)
        (log/info "The metric store has been down")))))
