(ns grenada.core
  "This namespace contains all sorts of stuff that hasn't be cleaned up and
  sorted into the appropriate namespaces yet."
  (:require [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [plumbing.core :refer [safe-get]]
            [grenada.config :refer [config]]
            grimoire.util
            [leiningen.pom :as pom]))

;;;; Pseudo config

;; TODO: Find a better place for this. (RM 2015-08-02)
(defn jar-name [artifact version]
  {:pre [artifact version]}
  (io/file (format "%s-%s-%s.jar"
                   artifact
                   version
                   (safe-get config :classifier))))


;;;; Miscellaneous helpers

(defn- ord-file-seq [fl]
  (filter #(.isFile %) (file-seq fl)))


;;;; Postprocessors (public API)

;; TODO: Correct problems with relative paths. .getParentFile only works with
;;       files that have more than one segment. However, when I converted to an
;;       absolute filename, .relativizePath complained that "other" was a
;;       different type of path. (RM 2015-06-24)
;; TODO: Find a better place for this. (RM 2015-08-02)
(defn jar-from-files
  "Takes the Grenada data from IN-DIR and packages them up in a JAR. Also
  creates a pom.xml with Maven coordinates from COORDS-OUT. Writes JAR and
  pom.xml to OUT-DIR."
  [in-dir out-dir {:keys [group artifact version] :as coords-out}]
  (let [jar-path (io/file out-dir (jar-name artifact version))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group artifact "pom.xml")
        in-dir-file (io/as-file in-dir)
        in-dir-parent (.getParentFile in-dir-file)
        files (ord-file-seq in-dir-file)
        files-map (into {} (map (fn [p]
                                  [p (jar/relativize-path in-dir-parent p)])
                                files))]
    (io/make-parents pom-path) ; Also takes care of parents for JAR file.
    (spit pom-path (-> coords-out
                       (assoc :name artifact) ; Because we're going back to lein
                       pom/make-pom))
    (jar/make-jar jar-path {:manifest-version "1.0"}
                  (conj files-map [pom-path pom-in-jar]))))

;; REVIEW: Should we throw this out? (RM 2015-08-02)
(defn deploy-jar [{artifact :name :keys [group version] :as coords} out-dir
                  [u p]]
  (aether/deploy
    :coordinates [(symbol group artifact)
                  version
                  :classifier (safe-get config :classifier)]
    :jar-file (io/file out-dir (jar-name artifact version))
    :pom-file (io/file out-dir "pom.xml")
    :repository {"clojars" {:url "https://clojars.org/repo"
                            :username u
                            :password p}}))
