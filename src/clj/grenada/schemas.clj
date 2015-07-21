(ns grenada.schemas
  (:require [schema.core :as s]))

(defn- ns-qualified? [kw-or-sym]
  (namespace kw-or-sym))


(defn adheres? [schema v]
  (nil? (s/check schema v)))

(def Fn (s/pred fn? "a fn"))

(def NSQKeyword (s/both s/Keyword
                        (s/pred ns-qualified? "has namespace")))

(def Vector (s/pred vector? "a vector"))
