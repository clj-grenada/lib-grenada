(ns grenada.postprocessors
  (:require [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [grenada
             [config :refer [config]]
             [schemas :as schemas]
             [utils :as gr-utils]]
            [grenada.utils.jar :as gr-jar]
            [plumbing.core :refer [safe-get]]
            [schema.core :as s]))

;; TODO: Correct problems with relative paths. .getParentFile only works with
;;       files that have more than one segment. However, when I converted to an
;;       absolute filename, .relativizePath complained that "other" was a
;;       different type of path. (RM 2015-06-24)
(s/defn ^:always-validate jar-from-files
  "Takes the Grenada data from IN-DIR and packages them up in a JAR. Also
  creates a pom.xml with Maven coordinates from COORDS-OUT. Writes JAR and
  pom.xml to OUT-DIR.

  Note that IN-DIR has to be a path with more than one segment. You can't use
  'a-dir', but './a-dir' should work."
  [in-dir out-dir {:keys [group artifact version] :as coords-out}
   :- schemas/JarCoordsWithDescr]
  (let [in-dir-file (io/as-file in-dir)
        in-dir-parent (.getParentFile in-dir-file)
        files (gr-utils/ordinary-file-seq in-dir-file)
        files-map (into {} (map (fn [p]
                                  [(jar/relativize-path in-dir-parent p)
                                   (jar/->file-entry p)])
                                files))]
    (gr-jar/jar-from-entries-map files-map out-dir coords-out)))

(s/defn ^:always-validate deploy-jar
  "Deploys a JAR file and a pom.xml found in OUT-DIR to Clojars.

  This procedure will deploy the JAR file whose file name matches COORDS.
  OUT-DIR can only contain one file called pom.xml and this one will be
  deployed. U and P have to be username and password for Clojars.

  The :description entry in COORDS will be ignored."
  [{:keys [artifact group version] :as coords} :- schemas/JarCoordsWithDescr
   out-dir [u p]]
  (aether/deploy
    :coordinates [(symbol group artifact)
                  version
                  :classifier (safe-get config :classifier)]
    :jar-file (io/file out-dir (gr-jar/jar-name artifact version))
    :pom-file (io/file out-dir "pom.xml")
    :repository {"clojars" {:url "https://clojars.org/repo"
                            :username u
                            :password p}}
    :transfer-listener :stdout))
