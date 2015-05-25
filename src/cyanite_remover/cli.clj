(ns cyanite-remover.cli
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [cyanite-remover.logging :as clog]
            [cyanite-remover.metric-store :as mstore]
            [cyanite-remover.path-store :as pstore]
            [cyanite-remover.core :as core]
            [org.spootnik.logconfig :as logconfig])
  (:gen-class))

(def cli-commands #{"remove-metrics" "remove-paths" "list-metrics" "list-paths"
                    "help"})

(defn- parse-rollups
  "Parse rollups."
  [rollups]
  (->> (str/split rollups #",")
       (map #(re-matches #"^((\d+):(\d+))$" %))
       (map #(if % [(Integer/parseInt (nth % 2))
                    (Integer/parseInt (nth % 3))] %))))

(defn- usage
  "Construct usage message."
  [options-summary]
  (->> ["Cyanite data removal tool"
        ""
        "Usage: "
        "  cyanite-remover [options] remove-metrics <tenant> <rollup:period,...> <path,...> <cassandra-host,...> <elasticsearch-url>"
        "  cyanite-remover [options] remove-paths <tenant> <path,...> <elasticsearch-url>"
        "  cyanite-remover [options] list-metrics <tenant> <rollup:period,...> <path,...> <cassandra-host,...> <elasticsearch-url>"
        "  cyanite-remover [options] list-paths <tenant> <path,...> <elasticsearch-url>"
        "  cyanite-remover help"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn- error-msg
  "Combine error messages."
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn- exit
  "Print message and exit with status."
  [status msg]
  (println msg)
  (System/exit status))

(defn- check-arguments
  [command arguments min max]
  (let [n-args (count arguments)]
    (when (or (< n-args min) (> n-args max))
      (exit 1 (error-msg
               [(format "Invalid number of arguments for the command \"%s\""
                        command)])))))

(defn- check-options
  "Check options."
  [command valid-options options]
  (doseq [option (keys options)]
    (when (not (contains? valid-options option))
      (exit 1 (error-msg
               [(format "Option \"--%s\" conflicts with the command \"%s\""
                        (name option) command)])))))

(defn- prepare-metrics-args
  "Prepare metrics arguments."
  [arguments options]
  (let [tenant (nth arguments 0)
        rollups (->> (nth arguments 1)
                     (parse-rollups)
                     (filter #(not (nil? %)))
                     (flatten)
                     (apply hash-map))
        paths (str/split (nth arguments 2) #",")
        cass-hosts (str/split (nth arguments 3) #",")
        es-url (nth arguments 4)]
    {:tenant tenant :rollups rollups :paths paths :cass-hosts cass-hosts
     :es-url es-url :options options}))

(defn- prepare-paths-args
  "Prepare paths arguments."
  [arguments options]
  (let [tenant (nth arguments 0)
        paths (str/split (nth arguments 1) #",")
        es-url (nth arguments 2)]
    {:tenant tenant :paths paths :es-url es-url :options options}))

(defn- run-remove-metrics
  "Run command 'remove-metrics'."
  [command arguments options summary]
  (check-arguments "remove-metrics" arguments 5 5)
  (check-options command #{:from :to :run :jobs :cassandra-keyspace
                           :cassandra-options :cassandra-channel-size
                           :cassandra-batch-size :cassandra-batch-rate
                           :elasticsearch-index :log-file :log-level
                           :disable-log :stop-on-error :disable-progress}
                 options)
  (when (and (:jobs options) (not (or (:from options) (:to options))))
    (exit 1 (error-msg
             ["Option \"--jobs\" requires \"--from\" and/or \"--to\""])))
  (let [{:keys [tenant rollups paths cass-hosts es-url
                options]} (prepare-metrics-args arguments options)]
    (core/remove-metrics tenant rollups paths cass-hosts es-url options)))

(defn- run-remove-paths
  "Run command 'remove-paths'."
  [command arguments options summary]
  (check-arguments "remove-paths" arguments 3 3)
  (check-options command #{:run :elasticsearch-index :log-file :log-level
                           :disable-log}
                 options)
  (let [{:keys [tenant paths es-url
                options]} (prepare-paths-args arguments options)]
    (core/remove-paths tenant paths es-url options)))

(defn- run-list-metrics
  "Run command 'list-metrics'."
  [command arguments options summary]
  (check-arguments "list-metrics" arguments 5 5)
  (check-options command #{:from :to :cassandra-keyspace :cassandra-options
                           :elasticsearch-index}
                 options)
  (let [{:keys [tenant rollups paths cass-hosts es-url
                options]} (prepare-metrics-args arguments options)]
    (core/list-metrics tenant rollups paths cass-hosts es-url options)))

(defn- run-list-paths
  "Run command 'list-paths'."
  [command arguments options summary]
  (check-arguments "list-paths" arguments 3 3)
  (check-options command #{:elasticsearch-index} options)
  (let [{:keys [tenant paths es-url
                options]} (prepare-paths-args arguments options)]
    (core/list-paths tenant paths es-url options)))

(defn- run-help
  "Run command 'help'."
  [command arguments options summary]
  (exit 0 (usage summary)))

(def cli-options
  [["-f" "--from FROM" "From time (Unix epoch)"
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 0 %) "Must be a number >= 0"]]
   ["-t" "--to TO" "To time (Unix epoch)"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   ["-r" "--run" "Force normal run (dry run using on default)"]
   [nil "--cassandra-keyspace KEYSPACE"
    (str "Cassandra keyspace. Default: " mstore/default-cassandra-keyspace)]
   ["-j" "--jobs JOBS" "Number of jobs to run simultaneously"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   ["-O" "--cassandra-options OPTIONS"
    "Cassandra options. Example: \"{:compression :lz4}\""
    :parse-fn #(read-string %)
    :validate [#(= clojure.lang.PersistentArrayMap (type %))]]
   [nil "--cassandra-channel-size SIZE"
    (str "Cassandra channel size. Default: "
         mstore/default-cassandra-channel-size)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   [nil "--cassandra-batch-size SIZE"
    (str "Cassandra batch size. Default: " mstore/default-cassandra-batch-size)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   [nil "--cassandra-batch-rate RATE" "Cassandra batch rate (batches per second)"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 101) "Must be a number between 1-100"]]
   [nil "--elasticsearch-index INDEX"
    (str "Elasticsearch index. Default: " pstore/default-es-index)]
   ["-l" "--log-file FILE" (str "Log file. Default: " clog/default-log-file)]
   ["-L" "--log-level LEVEL"
    (str "Log level (all, trace, debug, info, warn, error, fatal, off). "
         "Default: " clog/default-log-level)
    :validate [#(or (= (count %) 0)
                    (not= (get logconfig/levels % :not-found) :not-found))
               "Invalid log level"]]
   ["-S" "--stop-on-error" "Stop on first non-fatal error"]
   ["-P" "--disable-progress" "Disable progress bar"]
   ["-h" "--help" "Show this help"]])

(defn- run-command
  "Run command."
  [arguments options summary]
  (let [command (first arguments)]
    (when (not (contains? cli-commands command))
      (exit 1 (error-msg [(format "Unknown command: \"%s\"" command)])))
    (apply (resolve (symbol (str "cyanite-remover.cli/run-" command)))
           [command (drop 1 arguments) options summary])))

(defn -main
  "Main function."
  [& args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
     (or (< (count args) 1) (contains? options :help)) (exit 0 (usage summary))
     errors (exit 1 (error-msg errors)))
    ;; Run command
    (run-command arguments options summary)
    (clog/exit 0)))
