(ns grenada-lib.reading
  "A wrapper around edn/read-string with the configurations specific to
  Grenada."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]))

(defn read-string [s]
  (edn/read-string {:default (constantly :unknown-object-ignored)} s))
