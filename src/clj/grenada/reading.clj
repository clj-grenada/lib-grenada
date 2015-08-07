(ns grenada.reading
  "A wrapper around edn/read-string with the configurations specific to
  Grenada."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]
            [grenada.things :as t]))

(defn read-string [s]
  (edn/read-string {:readers {'g/t t/vec->thing
                              'guten/tag t/vec->thing}
                    :default (fn [t v]
                               :unknown-object-ignored)} s))
