(ns cyanite-remover.logging
  (:require [clojure.string :as str]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [org.spootnik.logconfig :as logconfig]))

(defmacro disable-logging!
  "Disable logging."
  []
  `(logconfig/start-logging! {:level "off" :console false :files ""}))

(disable-logging!)

(def ^:const default-log-file "cyanite-remover.log")
(def ^:const default-log-level "info")

(def print-log? (atom false))
(def disable-log? (atom false))
(def stop-on-error? (atom false))

(defn set-logging!
  "Set options."
  [options]
  (swap! print-log? (fn [_] (:disable-progress options @print-log?)))
  (swap! disable-log? (fn [_] (:disable-log options @disable-log?)))
  (swap! stop-on-error? (fn [_] (:stop-on-error options @stop-on-error?)))
  (if-not @disable-log?
    (logconfig/start-logging! {:level (:log-level options default-log-level)
                               :files [(:log-file options default-log-file)]})
    (disable-logging!)))

(defn disable-logging!
  "Disable logging."
  []
  (swap! print-log? (fn [_] true))
  (swap! disable-log? (fn [_] true))
  (swap! stop-on-error? (fn [_] true))
  (logconfig/start-logging! {:level "off" :console false :files ""}))

(defmacro log-log
  [f msg throwable]
  `(if (instance? Throwable ~throwable)
     (~f ~throwable ~msg)
     (~f ~msg)))

(defmacro log-print
  [msg throwable]
  `(do
     (println ~msg)
     (when (instance? Throwable ~throwable)
       (stacktrace/print-stack-trace ~throwable))))

(defn info-always
  "Always log info."
  [msg & [throwable]]
  (log-print msg throwable)
  (log-log log/info msg throwable))

(defn exit
  "Exit."
  [ret-code]
  (log/info "Shutting down agents...")
  (shutdown-agents)
  (log/info "Agents have been down")
  (let [exit-msg (format "Exiting with code %s..." ret-code)]
    (log/info exit-msg)
    (when (not= ret-code 0)
      (println exit-msg)))
  (System/exit ret-code))

(defn info
  "Log info."
  [msg & [throwable]]
  (when @print-log?
    (log-print msg throwable))
  (log-log log/info msg throwable))

(defn warning
  "Log warning."
  [msg & [throwable]]
  (when @print-log?
    (log-print msg throwable))
  (log-log log/warn msg throwable))

(defn error
  "Log error."
  [msg & [throwable]]
  (when (and (not @print-log?) @stop-on-error?)
    (newline))
  (when (or @print-log? @stop-on-error?)
    (log-print msg throwable))
  (log-log log/error msg throwable)
  (when @stop-on-error?
    (exit 1)))

(defn fatal
  "Log fatal."
  [msg & [throwable]]
  (when-not @print-log?
    (newline))
  (log-print msg throwable)
  (log-log log/fatal msg throwable)
  (exit 1))

(defn unhandled-error
  "Log unhandled error."
  [e]
  (fatal (str "Fatal error: " e) e))
