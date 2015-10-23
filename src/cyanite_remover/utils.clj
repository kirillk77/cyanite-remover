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
