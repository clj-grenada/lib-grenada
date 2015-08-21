(ns grenada.reading
  "A wrapper around edn/read-string with the configurations specific to
  Grenada."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]
            [grenada.things :as t]))

(defn read-string
  "Same as clj::clojure.edn/read-string, except that it can also read Things and
  #objects (replacing them with :unknown-object-ignored."
  [s]
  (edn/read-string {:readers {'g/t t/vec->thing
                              'guten/tag t/vec->thing}
                    :default (fn [t v]
                               :unknown-object-ignored)} s))
