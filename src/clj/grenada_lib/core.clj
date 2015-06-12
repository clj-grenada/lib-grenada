(ns grenada-lib.core
  (:require [clojure.java.io :as io]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            grimoire.util
            [leiningen.pom :as pom]
            ))

(def data-fnm "data.edn")

(defn coords->path [coords]
  (apply io/file (map grimoire.util/munge coords)))

(defn exp-map-fs-hier [m out-dir]
  (let [data-dir (io/file out-dir (coords->path (:coords m)))
        data-path (io/file data-dir data-fnm)]
    (io/make-parents data-path)
    (with-open [writer (io/writer data-path)]
      (binding [*out* writer] (prn m)))
    data-path))

(defn exp-data-fs-hier [data out-dir]
  (doseq [m data]
    (exp-map-fs-hier m out-dir)))

(defn out-jar [{artifact :name version :version}]
  (io/file (str artifact "-" version "-metadata.jar")))

(defn ord-file-seq [fl]
  (filter #(.isFile %) (file-seq fl)))

(defn jar-from-project
  "Scans the namespaces and vars from the project identified by PROJECT-IN (cf.
  Leiningen docs on 'project') and writes their Cmetadata to a
  directory-and-file structure. Packages these data into a JAR and writes a
  pom.xml. The package will have the Maven coordinates from COORDS-OUT.

  "
  [in-dir out-dir {artifact :name group :group version :version :as coords-out}]
  (let [jar-path (io/file out-dir (out-jar coords-out))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group artifact "pom.xml")
        in-dir-parent (.getParentFile in-dir)
        files (ord-file-seq in-dir)
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

; TODO: We need a way to uniquely specify which metadata JAR we want the
; metadata from.
(defn read-metadata [[group artifact version platform]])
