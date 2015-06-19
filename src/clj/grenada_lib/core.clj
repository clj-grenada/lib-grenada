(ns grenada-lib.core
  "This namespace contains all sorts of stuff that hasn't be cleaned up and
  sorted into the appropriate namespaces yet."
  (:require [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [plumbing.core :refer [safe-get]]
            [grenada-lib.config :refer [config]]
            grimoire.util
            [leiningen.pom :as pom]))

;;;; Pseudo config

(defn out-jar [{artifact :name version :version}]
  (io/file (str artifact "-" version "-metadata.jar")))


;;;; Miscellaneous helpers

(defn coords->path [coords]
  (apply io/file (map grimoire.util/munge coords)))

(defn ord-file-seq [fl]
  (filter #(.isFile %) (file-seq fl)))


;;;; A source

; TODO: We need a way to uniquely specify which metadata JAR we want the
; metadata from.
(defn read-metadata [where-to-look])


;;;; Exporter (public API)

(defn- exp-map-fs-hier [m out-dir]
  (let [data-dir (io/file out-dir (coords->path (:coords m)))
        data-path (io/file data-dir (safe-get config :datafile-name))]
    (io/make-parents data-path)
    (with-open [writer (io/writer data-path)]
      (binding [*out* writer] (prn m)))
    data-path))

(defn exp-data-fs-hier [data out-dir]
  (doseq [m data]
    (exp-map-fs-hier m out-dir)))


;;;; Postprocessors (public API)

(defn jar-from-files
  "Takes the Grenada data from IN-DIR and packages them up in a JAR. Also
  creates a pom.xml with Maven coordinates from COORDS-OUT. Writes JAR and
  pom.xml to OUT-DIR."
  [in-dir out-dir {artifact :name group :group version :version :as coords-out}]
  (let [jar-path (io/file out-dir (out-jar coords-out))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group artifact "pom.xml")
        in-dir-file (io/as-file in-dir)
        in-dir-parent (.getParentFile in-dir-file)
        files (ord-file-seq in-dir-file)
        files-map (into {} (map (fn [p]
                                  [p (jar/relativize-path in-dir-parent p)])
                                files))]
    (io/make-parents pom-path) ; Also takes care of parents for JAR file.
    (spit pom-path (pom/make-pom coords-out))
    (jar/make-jar jar-path {:manifest-version "1.0"}
                  (conj files-map [pom-path pom-in-jar]))))

(defn deploy-jar [{artifact :name :keys [group version] :as coords} out-dir
                  [u p]]
  (aether/deploy
    :coordinates [(symbol group artifact) version :classifier "metadata"]
    :jar-file (io/file out-dir (out-jar coords))
    :pom-file (io/file out-dir "pom.xml")
    :repository {"clojars" {:url "https://clojars.org/repo"
                            :username u
                            :password p}}))
