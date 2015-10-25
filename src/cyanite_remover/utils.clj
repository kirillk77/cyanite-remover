(ns cyanite-remover.utils
  (:require [clojure.string :as str]
            [throttler.core :as trtl]))

(defn string-or-empty
  "Return a passed string if the value is not nil and an empty string otherwise."
  [value string]
  (if value (str string value) ""))

(defn fn-or-trtlfn
  "Return a throttled function if the rate is not nil and a function otherwise."
  [f rate]
  (if rate (trtl/throttle-fn f rate :second) f))

;; https://stackoverflow.com/questions/14488150/how-to-write-a-dissoc-in-command-for-clojure
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  WILL BE present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (assoc m k newmap))
      m)
    (dissoc m k)))
