(ns grenada-lib.core
  (:require [clojure.java.io :as io]
            [medley.core :refer [dissoc-in]]
            [clojure.tools.namespace.find :as tns.find]
            [cemerick.pomegranate.aether :as aether]
            [darkestperu.jar :as jar]
            [leiningen.pom :as pom]
            [plumbing.core :as plumbing :refer [fnk]]))

(def metadata-dirnm "grenada-data")
(def data-fnm "data.edn")

(declare find-entities-clj)

(defn clj-namespace-src [dir-path]
  (tns.find/find-namespaces-in-dir (io/as-file dir-path)))

(defn nssym->nsmap [s]
  {:name (name s)
   :level :grimoire.things/namespace
   :coords-suffix ["clj" (name s)]})

(defn nssym->deftups [nssym]
  (->> nssym
      find-ns
      ns-interns
      keys
      (map (fn [s] [nssym s]))))

(defn deftup->defmap [[nssym defsym]]
  {:name (name defsym)
   :level :grimoire.things/def
   :coords-suffix ["clj" (name nssym) (name defsym)]})

(defmulti merge-in-meta :level)

; Urgh.
(defmethod merge-in-meta :grimoire.things/def
  [{[_ nsp-name def-name] :coords-suffix :as data}]
  (->> def-name
       symbol
       (ns-resolve (find-ns (symbol nsp-name)))
       meta
       (plumbing/assoc-when data :cmeta)))

(defmethod merge-in-meta :grimoire.things/namespace
  [{[_ nsp-name] :coords-suffix :as data}]
  (->> nsp-name
       symbol
       find-ns
       meta
       (plumbing/assoc-when data :cmeta)))

(defn complete-coords
  [{:keys [coords-suffix] :as ent-data} {artifact :name :keys [group version]}]
  (-> ent-data
      (dissoc :coords-suffix)
      (assoc :coords (into [group artifact version] coords-suffix))))

(def clj-entity-src-graph
  {:nssyms (fnk [dir-path] (clj-namespace-src dir-path))
   :nsmaps (fnk [nssyms] (map nssym->nsmap nssyms))
   :deftups (fnk [nssyms] (mapcat nssym->deftups nssyms))
   :defmaps (fnk [deftups] (map deftup->defmap deftups))
   :bare-ents (fnk [nsmaps defmaps] (concat nsmaps defmaps))
   :ents-with-meta (fnk [bare-ents] (map merge-in-meta bare-ents))
   :entity-maps (fnk [ents-with-meta artifact-coords]
                     (map #(complete-coords % artifact-coords)
                          ents-with-meta))})

(defn ensure-exists [dir-path]
  (when-not (.exists dir-path)
    (.mkdirs dir-path)))

(defn coords->path [coords]
  (apply io/file coords))

(defn write-data [data root]
  (let [data-dir (io/file root metadata-dirnm (coords->path (:coords data)))
        data-path (io/file data-dir data-fnm)]
    (ensure-exists data-dir)
    (with-open [writer (io/writer data-path)]
      (binding [*out* writer] (prn data)))
    data-path))

(defn out-dir [root]
  (io/file root "target" "grenada"))

(defn out-jar [{artifact :name version :version}]
  (io/file (str artifact "-" version "-metadata.jar")))

(defn jar-from-project
  "Scans the namespaces and vars from the project identified by PROJECT-IN (cf.
  Leiningen docs on 'project') and writes their Cmetadata to a
  directory-and-file structure. Packages these data into a JAR and writes a
  pom.xml. The package will have the Maven coordinates from COORDS-OUT.

  Note: tools.namespace supports only one namespace per file. We inherit this
  limitation. This means, if you have a file that defines more than one
  namespace, jar-from-project will only be able to extract the first. In future
  versions of this library, you will be able to plug in your own namespace
  scanner, in order to overcome this."
  [{artifact-i :name group-i :group version-i :version root :root :as project-in}
   {artifact-o :name group-o :group version-o :version :as coords-out}]
  (let [out-dir  (out-dir root)
        jar-path (io/file out-dir (out-jar coords-out))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group-o artifact-o "pom.xml")
        files
        (->> (find-entities-clj root)
             (map merge-in-meta)
             (map #(complete-coords % project-in))
             (map #(write-data % root))
             doall)
        files-map (into {} (map (fn [p] [p (jar/relativize-path root p)])
                                files))]
    (ensure-exists out-dir)
    (spit pom-path (pom/make-pom coords-out))
    (jar/make-jar jar-path {:manifest-version "1.0"}
                  (conj files-map [pom-path pom-in-jar]))))

(defn deploy-jar [{artifact :name :keys [group version] :as coords} root
                  [u p]]
  (aether/deploy
    :coordinates [(symbol group artifact) version :classifier "metadata"]
    :jar-file (io/file (out-dir root) (out-jar coords))
    :pom-file (io/file (out-dir root) "pom.xml")
    :repository {"clojars" {:url "https://clojars.org/repo"
                            :username u
                            :password p}}))

; TODO: We need a way to uniquely specify which metadata JAR we want the
; metadata from.
(defn read-metadata [[group artifact version platform]])
