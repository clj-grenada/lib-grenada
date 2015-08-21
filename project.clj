(defproject org.clj-grenada/lib-grenada "1.0.0-rc.4"
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
                    ; When bumping these, also change the entry in config.clj.
                 [org.clj-grenada/darkestperu "1.0.0-rc.2"]
                 [com.cemerick/pomegranate "0.3.0"]

                 ;; For new versions of guten-tag, look if the print-method
                 ;; changed and potentially adjust
                 ;; grenada.core/print-ataggedval.
                 [org.clojars.rmoehn/guten-tag "0.1.5"]
                 [org.clojars.rmoehn/lib-grimoire "0.10.3"]

                 ;; WARNING: I'm using features of Fipp that are ‘subject to
                 ;;          change’ according to Fipp's README.
                 [fipp "0.6.2"]
                 [prismatic/schema "0.4.3"]
                 [prismatic/plumbing "0.4.4"]
                 [slingshot "0.12.2"]]

  :source-paths ["src/clj"]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]]
                   :source-paths ["dev"]}}

  :codox {:sources ["src/clj"]
          :output-dir "api-docs"
          :src-dir-uri "https://github.com/clj-grenada/lib-grenada/blob/master/"
          :homepage-uri "https://github.com/clj-grenada/lib-grenada/tree/master/"
          :src-linenum-anchor-prefix "L"})
