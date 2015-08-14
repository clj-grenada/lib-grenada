(defproject org.clj-grenada/lib-grenada "0.3.2"
  :description "A library for processing Clojure metadata"
  :url "https://github.com/clj-grenada/lib-grenada"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [dire "0.5.3"]
                 [leiningen "2.5.1"] ; Sorry for the strange dependency.
                                     ; TODO: Think about librarizing the
                                     ;       required code. (RM 2015-06-27)
                 [medley "0.6.0"]
                    ; Bump once Leiningen runs on Clojure 1.7.0.
                 [org.projectodd.shimdandy/shimdandy-api "1.1.0"]
                 [org.projectodd.shimdandy/shimdandy-impl "1.1.0"]
                 [org.clj-grenada/darkestperu "0.2.0-SNAPSHOT"]
                 [com.cemerick/pomegranate "0.3.0"]

                 ;; For new versions of guten-tag, look if the print-method
                 ;; changed and potentially adjust
                 ;; grenada.core/print-ataggedval.
                 [me.arrdem/guten-tag "0.1.4"]
                 [org.clojure-grimoire/lib-grimoire "0.10.2"]

                 ;; WARNING: I'm using features of Fipp that are ‘subject to
                 ;;          change’ according to Fipp's README.
                 [fipp "0.6.2"]
                 [prismatic/schema "0.4.3"]
                 [prismatic/plumbing "0.4.4"]
                 [slingshot "0.12.2"]]
  :plugins [[org.clj-grenada/lein-datadoc "0.1.0-SNAPSHOT"]]

  :source-paths ["src/clj"]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]]
                   :source-paths ["dev"]}})
