(ns grenada.config
  "Static configuration for Grenada.")

;;; TODO: Make these things configurable from the outside. (RM 2015-06-19)

(def config
  {:classifier "datadoc"

   :datafile-name "data.edn"

   ;; Credits: https://github.com/boot-clj/boot/blob/c244cc6cffea48ce2912706567b3bc41a4d387c7/boot/aether/src/boot/aether.clj
   :default-repositories
   {"clojars"       "https://clojars.org/repo/"
    "maven-central" "https://repo1.maven.org/maven2/"}

   :jar-root-dir "datadoc"

   :shimdandy-version "1.1.0"})
