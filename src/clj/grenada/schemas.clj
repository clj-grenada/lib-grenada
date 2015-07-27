(ns grenada.schemas
  (:require [schema.core :as s]))

(defn- ns-qualified? [kw-or-sym]
  (namespace kw-or-sym))


(defn adheres? [schema v]
  (nil? (s/check schema v)))

;;; TODO: (Here and in other places.) Switch to s/defschema. (RM 2015-07-24)

(def Fn (s/pred fn? "a fn"))

(def NSQKeyword (s/both s/Keyword
                        (s/pred ns-qualified? "has namespace")))

;; REVIEW: Make sure this works as I think it does. Or find a prettier way to do
;;         it, for that matter. (RM 2015-07-24)
(def Schema
  "A schema for Prismatic Schemas."
  (s/pred (fn [x] (try (s/checker x)
                       true
                       (catch Exception _
                         false)))
          "Is a valid Prismatic Schema."))

(def Vector (s/pred vector? "a vector"))
