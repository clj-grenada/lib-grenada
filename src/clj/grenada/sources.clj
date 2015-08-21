(ns grenada.sources
  "Procedures for obtaining collections of Things from various places."
  (:require [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [grenada
             [config :refer [config]]
             [reading :as gr-reading]]
            [plumbing.core :as plumbing :refer [safe-get]]
            [schema.core :as s])
  (:import java.io.File))

(s/defschema LeinDepSpec
  "A Leiningen dependency specification. – Same as in project.clj, also used by
  Pomegranate."
  (s/either [(s/one s/Symbol "group-artifact") (s/one s/Str "version")]
            [(s/one s/Symbol "group-artifact") (s/one s/Str "version")
             (s/one (s/eq :classifier) "cl-key") (s/one s/Str "classifier")]))

(s/defn resolve-artifact :- File
  "Returns a File pointing to the JAR file described by DEP-SPEC.

  If the JAR isn't present in the local Maven repository, it will be downloaded
  from one of the repositories configured in clj::grenada.config/config."
  [dep-spec :- LeinDepSpec]
  (let [files (map #(safe-get (meta %) :file)
                   (aether/resolve-artifacts
                     :coordinates [dep-spec]
                     :repositories (safe-get config :default-repositories)))]
    (when (zero? (count files))
      (throw (IllegalArgumentException. (str "Couldn't resolve dependency"
                                             dep-spec))))
    (when (> (count files) 1)
      (throw (AssertionError.
               "A dep-spec shouldn't resolve to more than one file.")))
    (first files)))


;;;; Main API

(defn from-jar
  "Returns a sequence of Things as read from from JAR-FILEABLE. JAR-FILEABLE is
  anything that can be passed to clj::darkestperu.jar/jar-seq.

  Note that this procedure doesn't check whether the Things read from the JAR
  are valid."
  [jar-fileable]
  (->> jar-fileable
       jar/jar-seq
       (filter #(re-matches #"(?xms) \A .* \.edn \z" %))
       (map #(jar/slurp-from-jar jar-fileable %))
       (map gr-reading/read-string)))

(s/defn ^:always-validate from-depspec
  "Returns a sequence of Things as read from the JAR file denoted by DEP-SPEC.

  Note that this procedure doesn't check whether the Things read from the JAR
  are valid."
  [depspec :- LeinDepSpec]
  (let [file (resolve-artifact depspec)]
    (from-jar file)))
