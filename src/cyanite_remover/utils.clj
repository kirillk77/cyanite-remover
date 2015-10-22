(ns cyanite-remover.utils
  (:require [clojure.string :as str]))

(defn string-or-empty
  "Return a passed string if value is not nil and an empty string otherwise."
  [value string]
  (if value (str string value) ""))
