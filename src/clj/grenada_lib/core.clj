(ns grenada-lib.core
  "This namespace contains all sorts of stuff that hasn't be cleaned up and
  sorted into the appropriate namespaces yet."
  (:require [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [plumbing.core :refer [safe-get]]
            [grenada-lib.config :refer [config]]
            [grenada-lib.reading :as reading]
            grimoire.util
            [leiningen.pom :as pom]))

;;;; Pseudo config

(defn out-jar [{artifact :name version :version}]
  (io/file (str artifact "-" version "-metadata.jar")))


;;;; Miscellaneous helpers

(defn ord-file-seq [fl]
  (filter #(.isFile %) (file-seq fl)))


;;;; A predicate (to be made redundant by Guten-tag)

(defn def? [m]
  (= (safe-get m :level) :grimoire.things/def))


;;;; A source

;; TODO: Support reading from JARs. (RM 2015-06-19)
(defn read-metadata [where-to-look]
  (mapcat #(reading/read-string (slurp %))
          (ord-file-seq (io/file where-to-look))))


;;;; Postprocessors (public API)

;; TODO: Correct problems with relative paths. .getParentFile only works with
;;       files that have more than one segment. However, when I converted to an
;;       absolute filename, it .relativizePath complained that "other" was a
;;       different type of path. (RM 2015-06-24)
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

;; TODO: If we're staying with Grenada, change classifier to "grenadata". (RM
;;       2015-06-23)
(defn deploy-jar [{artifact :name :keys [group version] :as coords} out-dir
                  [u p]]
  (aether/deploy
    :coordinates [(symbol group artifact) version :classifier "metadata"]
    :jar-file (io/file out-dir (out-jar coords))
    :pom-file (io/file out-dir "pom.xml")
    :repository {"clojars" {:url "https://clojars.org/repo"
                            :username u
                            :password p}}))
