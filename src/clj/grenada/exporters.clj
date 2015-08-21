(ns grenada.exporters
  "Procedures for writing collections of Things to disk in various formats.

  See also grenada.exporters.pretty."
  (:require [clojure.java.io :as io]
            [darkestperu.jar :as jar]
            [plumbing.core :as plumbing :refer [safe-get]]
            [schema.core :as s]
            grimoire.util
            [grenada
             [config :refer [config]]
             [schemas :as schemas]
             [things :as t]
             [utils :as gr-utils]]
            [grenada.utils.jar :as gr-jar]))

;;;; Miscellaneous helpers

(defn- coords->path
  "Returns a File with a relative path from the given coords. The result can be
  used to store a Thing on disk, for example.

  Note that Grimoire only munges the Def coordinate (Find in Grenada), whereas
  this munges all. The result are quite ugly paths with version strings looking
  like '1%2E6%2E0'. However, weird characters might occur in almost all
  coordinates, so I don't understand why we should limit munging to the Def
  coordinate."
  [coords]
  (apply io/file (map grimoire.util/munge coords)))


;;;; Printing primitives

(defn prn-spit
  "Like clj::clojure.core/spit, but uses clj::clojure.core/prn for printing
  instead of whatever spit uses."
  [path x]
  (with-open [writer (io/writer path)]
    (binding [*out* writer] (prn x))))


;;;; Hierarchical filesystem exporter

(defn- exp-map-fs-hier
  "Writes Thing M to its proper place below OUT-DIR."
  [m out-dir]
  (let [data-dir (io/file out-dir (coords->path (safe-get m :coords)))
        data-path (io/file data-dir (safe-get config :datafile-name))]
    (io/make-parents data-path)
    (prn-spit data-path m)
    data-path))

(defn fs-hier
  "Writes a collection of Things to their appropriate places below OUT-DIR.

  Read them back like this:

  ````````clojure
  (->> out-dir
       grenada.utils/ordinary-file-seq
       (map slurp)
       (map grenada.reading/read-string))
  ````````"
  [data out-dir]
  (doseq [m data]
    (exp-map-fs-hier m out-dir)))


;;;; Flat filesystem exporters

(defn fs-flat
  "Writes a collection of Things to OUT-FILE.

  Read them back like this:
  ```````clojure
  (->> out-file
       slurp
       grenada.reading/read-string)
  ```````

  See also clj::grenada.exporters.pretty/pprint-fs-flat unless you're working in
  pre-1.7.0 Clojure."
  [data out-file]
  (prn-spit out-file data))


;;;; JAR exporter

(defn- thing->jar-entry
  "Returns the entry for T for the entries-map to be given to
  clj::grenada.utils.jar/jar-from-entries-map."
  [t]
  [(gr-utils/str-file
     (safe-get config :jar-root-dir)
     (coords->path (safe-get t :coords))
     (safe-get config :datafile-name))
   (jar/->string-entry (prn-str t))])

(s/defn ^:always-validate jar
  "Creates a Datadoc JAR containing the THINGS and a pom.xml for this JAR in
  OUT-DIR.

  The JAR's filename and the pom.xml will be derived from COORDS-OUT."
  [things out-dir coords-out :- schemas/JarCoordsWithDescr]
  (gr-jar/jar-from-entries-map (->> things
                                    (map thing->jar-entry)
                                    (into {}))
                               out-dir
                               coords-out))
