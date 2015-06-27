(defproject org.clj-grenada/lib-grenada "0.2.0-SNAPSHOT"
  :description "A library for processing Clojure metadata"
  :url "https://github.com/clj-grenada/lib-grenada"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0-RC2"]
                 [leiningen "2.5.1"] ; Sorry for the strange dependency.
                                     ; TODO: Think about librarizing the
                                     ;       required code. (RM 2015-06-27)
                 [org.projectodd.shimdandy/shimdandy-api "1.1.0"]
                 [org.projectodd.shimdandy/shimdandy-impl "1.1.0"]
                 [trammel "0.8.0"]
                 [org.clj-grenada/darkestperu "0.1.0-SNAPSHOT"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [me.arrdem/guten-tag "0.1.0"]
                 [org.clojure-grimoire/lib-grimoire "0.9.2"]
                 [prismatic/schema "0.4.3"]
                 [prismatic/plumbing "0.4.4"]

                 [org.reflections/reflections "0.9.9-RC1"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                   ; Otherwise we get NoClassDefFoundErrors from Reflections.
                 ]

  :source-paths ["src/clj"]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]]
                   :source-paths ["dev"]}})
