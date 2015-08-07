(ns grenada.sources
  (:require [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [grenada
             [config :refer [config]]
             [reading :as gr-reading]]
            [plumbing.core :as plumbing :refer [safe-get]]
            [schema.core :as s])
  (:import java.io.File))

(s/defschema LeinDepSpec
  (s/either [(s/one s/Symbol "group-artifact") (s/one s/Str "version")]
            [(s/one s/Symbol "group-artifact") (s/one s/Str "version")
             (s/one (s/eq :classifier) "cl-key") (s/one s/Str "classifier")]))

(s/defschema JFile
  (s/pred #(instance? File %) "a Java File"))

(s/defn resolve-artifact :- JFile [dep-spec :- LeinDepSpec]
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

(defn from-jar [jar-fileable]
  (->> jar-fileable
       jar/jar-seq
       (filter #(re-matches #"(?xms) \A .* \.edn \z" %))
       (map #(jar/slurp-from-jar jar-fileable %))
       (map gr-reading/read-string)))

(s/defn ^:always-validate from-depspec
  "

  Note that this procedure doesn't check whether the Things read from the JAR
  are valid."
  [depspec :- LeinDepSpec]
  (let [file (resolve-artifact depspec)]
    (from-jar file)))
