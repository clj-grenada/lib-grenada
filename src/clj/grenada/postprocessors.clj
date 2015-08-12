(ns grenada.postprocessors
  (:require [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [grenada
             [config :refer [config]]
             [utils :as gr-utils]]
            [grenada.utils.jar :as gr-jar]
            [plumbing.core :refer [safe-get]]))

;; TODO: Correct problems with relative paths. .getParentFile only works with
;;       files that have more than one segment. However, when I converted to an
;;       absolute filename, .relativizePath complained that "other" was a
;;       different type of path. (RM 2015-06-24)
;; TODO: Document the feature that one can pass additional entries for the POM
;;       file in coords-out. – Refer to exporters/jar. (RM 2015-08-07)
(defn jar-from-files
  "Takes the Grenada data from IN-DIR and packages them up in a JAR. Also
  creates a pom.xml with Maven coordinates from COORDS-OUT. Writes JAR and
  pom.xml to OUT-DIR."
  [in-dir out-dir {:keys [group artifact version] :as coords-out}]
  (let [in-dir-file (io/as-file in-dir)
        in-dir-parent (.getParentFile in-dir-file)
        files (gr-utils/ordinary-file-seq in-dir-file)
        files-map (into {} (map (fn [p]
                                  [(jar/relativize-path in-dir-parent p)
                                   (jar/->file-entry p)])
                                files))]
    (gr-jar/jar-from-entries-map files-map out-dir coords-out)))

;; REVIEW: Should we throw this out? (RM 2015-08-02)
(defn deploy-jar [{artifact :name :keys [group version] :as coords} out-dir
                  [u p]]
  (aether/deploy
    :coordinates [(symbol group artifact)
                  version
                  :classifier (safe-get config :classifier)]
    :jar-file (io/file out-dir (gr-jar/jar-name artifact version))
    :pom-file (io/file out-dir "pom.xml")
    :repository {"clojars" {:url "https://clojars.org/repo"
                            :username u
                            :password p}}))
