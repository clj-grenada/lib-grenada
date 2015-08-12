(ns grenada.exporters
  "

  See also grenada.exporters.pretty."
  (:require [clojure.java.io :as io]
            [darkestperu.jar :as jar]
            [plumbing.core :as plumbing :refer [safe-get]]
            grimoire.util
            [grenada
             [config :refer [config]]
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

(defn prn-spit [path x]
  (with-open [writer (io/writer path)]
    (binding [*out* writer] (prn x))))


;;;; Hierarchical filesystem exporter

(defn- exp-map-fs-hier [m out-dir]
  (let [data-dir (io/file out-dir (coords->path (safe-get m :coords)))
        data-path (io/file data-dir (safe-get config :datafile-name))]
    (io/make-parents data-path)
    (prn-spit data-path m)
    data-path))

(defn fs-hier [data out-dir]
  (doseq [m data]
    (exp-map-fs-hier m out-dir)))


;;;; Flat filesystem exporters

(defn fs-flat [data out-file]
  (prn-spit out-file data))


;;;; JAR exporter

(defn- thing->jar-entry [t]
  [(gr-utils/str-file
     (safe-get config :jar-root-dir)
     (coords->path (safe-get t :coords))
     (safe-get config :datafile-name))
   (jar/->string-entry (prn-str t))])

;; TODO: Document the feature that one can pass additional entries for the POM
;;       file in coords-out. (RM 2015-08-07)
(defn jar [things out-dir coords-out]
  (gr-jar/jar-from-entries-map (->> things
                                    (map thing->jar-entry)
                                    (into {}))
                               out-dir
                               coords-out))
