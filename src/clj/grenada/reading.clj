(ns grenada.reading
  "A wrapper around edn/read-string with the configurations specific to
  Grenada."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]
            [grenada.things :as t]))

(defn read-string
  "Same as clj::clojure.edn/read-string, except that it can also read Things and
  #objects, replacing them with :unknown-object-ignored.

  OPTS is the same as for clj::clojure.edn/read-string, letting you add to or
  override the options this function provides."
  ([s] (read-string {} s))
  ([opts s]
   (let [readers (merge {'g/t t/vec->thing
                         'guten/tag t/vec->thing}
                        (get opts :readers))
         complete-opts
         (-> {:default (fn [t v]
                         :unknown-object-ignored)}
             (merge opts)
             (assoc :readers readers))]
     (edn/read-string complete-opts s))))
