(ns grenada.config
  "Static configuration for Grenada.")

;;; TODO: Make these things configurable from the outside. (RM 2015-06-19)

(def config
  {;; Classifier for JARs with Grenada data
   :classifier "datadoc"

   ;; Directory in Datadoc JAR under which to put the Grenada data.
   :jar-root-dir "datadoc"

   ;; Name of the file containing metadata in hierarchical filesystem structure.
   :datafile-name "data.edn"

   ;; Remote repositories to use for resolving artifacts.
   ;; Credits: https://github.com/boot-clj/boot/blob/c244cc6cffea48ce2912706567b3bc41a4d387c7/boot/aether/src/boot/aether.clj
   :default-repositories
   {"clojars"       "https://clojars.org/repo/"
    "maven-central" "https://repo1.maven.org/maven2/"}

   ;; Shimdandy version to load into separate Clojure runtime. – Must be the
   ;; same as in project.clj.
   :shimdandy-version "1.1.0"})
