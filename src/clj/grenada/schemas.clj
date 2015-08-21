(ns grenada.schemas
  "Schemas that are used in more than one place in Grenada."
  (:require [schema.core :as s]))

;;;; Private helper

(defn- ns-qualified?
  "Returns true if KW-OR-SYM is a namespace-qualified keyword or symbol, false
  otherwise."
  [kw-or-sym]
  (namespace kw-or-sym))


;;;; Public helper

(defn adheres?
  "Returns true if V adheres to SCHEMA, false otherwise."
  [schema v]
  (nil? (s/check schema v)))


;;;; Schemas

(s/defschema Fn
  "A Clojure fn."
  (s/pred fn? "a fn"))

(s/defschema NSQKeyword
  "A namespace-qualified keyword."
  (s/both s/Keyword (s/pred ns-qualified? "has namespace")))

;; REVIEW: Make sure this works as I think it does. Or find a prettier way to do
;;         it, for that matter. (RM 2015-07-24)
(s/defschema Schema
  "A schema for Prismatic Schemas."
  (s/pred (fn [x] (try (s/checker x)
                       true
                       (catch Exception _
                         false)))
          "Is a valid Prismatic Schema."))

(s/defschema Vector
  "A vector."
  (s/pred vector? "a vector"))

(s/defschema JarCoords
  ":group, :artifact and :version correspond to the Maven coordinates groupId,
  artifactId and version."
  {:group s/Str
   :artifact s/Str
   :version s/Str})

;; See grenada.utils.jar/jar-from-entries-map for a todo item.
(s/defschema JarCoordsWithDescr
  ":description will end up in pom.xml as <description>…</description>."
  (assoc JarCoords
         (s/optional-key :description) s/Str))
