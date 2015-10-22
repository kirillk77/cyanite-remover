(ns cyanite-remover.cli
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [cyanite-remover.logging :as clog]
            [cyanite-remover.metric-store :as mstore]
            [cyanite-remover.path-store :as pstore]
            [cyanite-remover.core :as core]
            [org.spootnik.logconfig :as logconfig])
  (:gen-class))

(def cli-commands #{"remove-metrics" "remove-paths" "remove-obsolete-data"
                    "list-metrics" "list-paths" "list-obsolete-data" "help"})

(defn- parse-rollups
  "Parse rollups."
  [rollups]
  (->> (str/split rollups #",")
       (map #(re-matches #"^((\d+):(\d+))$" %))
       (map #(if %
               (let [seconds-per-point (Integer/parseInt (nth % 2))
                     retention (Integer/parseInt (nth % 3))
                     period (/ retention seconds-per-point)]
                 [seconds-per-point period])
               %))))

(defn- usage
  "Construct usage message."
  [options-summary]
  (->> ["A Cyanite data removal tool"
        ""
        "Usage: "
        "  cyanite-remover [options] remove-metrics <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>"
        "  cyanite-remover [options] remove-paths <tenant> <path,...> <elasticsearch_url>"
        "  cyanite-remover [options] remove-obsolete-data <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>"
        "  cyanite-remover [options] list-metrics <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>"
        "  cyanite-remover [options] list-paths <tenant> <path,...> <elasticsearch_url>"
        "  cyanite-remover [options] list-obsolete-data <tenant> <rollup,...> <path,...> <cassandra_host,...> <elasticsearch_url>"
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
  (if (= status 0)
    (println msg)
    (binding [*out* *err*]
      (println msg)))
  (System/exit status))

(defn- check-arguments
  "Check arguments."
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
    (when (and (not (contains? valid-options option))
               (not (= option :raw-arguments)))
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
        paths (str/split (nth arguments 2) #";")
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
  (check-options command #{:from :to :run :exclude-paths :jobs :sort
                           :cassandra-keyspace :cassandra-options
                           :cassandra-channel-size :cassandra-batch-size
                           :cassandra-batch-rate :elasticsearch-index
                           :elasticsearch-scroll-batch-size
                           :elasticsearch-scroll-batch-rate
                           :log-file :log-level :disable-log :stop-on-error
                           :disable-progress}
                 options)
  (when (and (:jobs options) (not (or (:from options) (:to options))))
    (exit 1 (error-msg
             ["Option \"--jobs\" requires \"--from\" and/or \"--to\""])))
  (let [{:keys [tenant rollups paths cass-hosts es-url
                options]} (prepare-metrics-args arguments options)]
    (core/remove-metrics tenant rollups paths cass-hosts es-url options)))

(defn- run-remove-obsolete-data
  "Run command 'remove-obsolete-data'."
  [command arguments options summary]
  (check-arguments "remove-obsolete-data" arguments 5 5)
  (check-options command #{:threshold :run :exclude-paths :sort :jobs
                           :cassandra-keyspace :cassandra-options
                           :cassandra-channel-size :cassandra-batch-size
                           :cassandra-batch-rate :elasticsearch-index
                           :elasticsearch-scroll-batch-size
                           :elasticsearch-scroll-batch-rate
                           :log-file :log-level :disable-log :stop-on-error
                           :disable-progress}
                 options)
  (let [{:keys [tenant rollups paths cass-hosts es-url
                options]} (prepare-metrics-args arguments options)]
    (core/remove-obsolete-data tenant rollups paths cass-hosts es-url options)))

(defn- run-remove-paths
  "Run command 'remove-paths'."
  [command arguments options summary]
  (check-arguments "remove-paths" arguments 3 3)
  (check-options command #{:run :exclude-paths :sort :jobs :elasticsearch-index
                           :elasticsearch-scroll-batch-size
                           :elasticsearch-scroll-batch-rate :log-file
                           :log-level :disable-log :disable-progress}
                 options)
  (let [{:keys [tenant paths es-url
                options]} (prepare-paths-args arguments options)]
    (core/remove-paths tenant paths es-url options)))

(defn- run-list-metrics
  "Run command 'list-metrics'."
  [command arguments options summary]
  (check-arguments "list-metrics" arguments 5 5)
  (check-options command #{:from :to :exclude-paths :sort :cassandra-keyspace
                           :cassandra-options :elasticsearch-index
                           :elasticsearch-scroll-batch-size
                           :elasticsearch-scroll-batch-rate}
                 options)
  (let [{:keys [tenant rollups paths cass-hosts es-url
                options]} (prepare-metrics-args arguments options)]
    (core/list-metrics tenant rollups paths cass-hosts es-url options)))

(defn- run-list-paths
  "Run command 'list-paths'."
  [command arguments options summary]
  (check-arguments "list-paths" arguments 3 3)
  (check-options command #{:exclude-paths :sort :elasticsearch-index
                           :elasticsearch-scroll-batch-size
                           :elasticsearch-scroll-batch-rate} options)
  (let [{:keys [tenant paths es-url
                options]} (prepare-paths-args arguments options)]
    (core/list-paths tenant paths es-url options)))

(defn- run-list-obsolete-data
  "Run command 'list-obsolete-data'."
  [command arguments options summary]
  (check-arguments "list-obsolete-data" arguments 5 5)
  (check-options command #{:threshold :exclude-paths :sort :jobs
                           :cassandra-keyspace :cassandra-options
                           :elasticsearch-index}
                 options)
  (let [{:keys [tenant rollups paths cass-hosts es-url
                options]} (prepare-metrics-args arguments options)]
    (core/list-obsolete-data tenant rollups paths cass-hosts es-url options)))

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
   ["-T" "--threshold THRESHOLD" (str "Threshold in seconds. Default: "
                                      core/default-obsolete-metrics-threshold)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   ["-r" "--run" "Force normal run (dry run using on default)"]
   ["-e" "--exclude-paths PATHS"
    "Exclude path. Example: \"requests.apache.*;sum.cpu.?\""
    :parse-fn #(str/split % #";")]
   ["-s" "--sort" "Sort paths in alphabetical order"]
   ["-j" "--jobs JOBS" "Number of jobs to run simultaneously"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   [nil "--cassandra-keyspace KEYSPACE"
    (str "Cassandra keyspace. Default: " mstore/default-cassandra-keyspace)]
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
   [nil "--elasticsearch-scroll-batch-size SIZE"
    (str "Elasticsearch scroll batch size. Default: "
         pstore/default-es-scroll-batch-size)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
   [nil "--elasticsearch-scroll-batch-rate RATE"
    "Elasticsearch scroll batch rate (batches per second)"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number > 0"]]
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
    ;; Add raw arguments to options
    (let [options (assoc options :raw-arguments args)]
      ;; Run command
      (run-command arguments options summary))
    (clog/exit 0)))
