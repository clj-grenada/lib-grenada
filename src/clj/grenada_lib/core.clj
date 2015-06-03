(ns grenada-lib.core
  (:require [clojure.java.io :as io]
            [medley.core :refer [dissoc-in]]
            [clojure.tools.namespace.find :as tns.find]
            [darkestperu.jar :as jar]
            [leiningen.pom :as pom]))

(def metadata-dirnm "grenada-data")
(def data-fnm "data.edn")

;; TODO: We'll have to separate clj and cljc files.
(defn find-entities-clj [dir-path]
  (let [namespaces (tns.find/find-namespaces-in-dir (io/as-file dir-path))]
    (concat
      (map (fn [s] {:name (name s)
                    :level :grimoire.things/namespace
                    :coords-suffix ["clj" (name s)]})
           namespaces)
      (mapcat (fn [namesp]
                (map (fn [s] {:name (name s)
                              :level :grimoire.things/def
                              :coords-suffix ["clj" (name namesp) (name s)]})
                     (keys (ns-interns (find-ns namesp)))))
              namespaces))))

(defmulti merge-in-meta :level)

; Urgh.
(defmethod merge-in-meta :grimoire.things/def
  [{[_ namesp def-name] :coords-suffix :as data}]
  (if-let [cmeta (meta (ns-resolve (find-ns (symbol namesp))
                                   (symbol def-name)))]
    (assoc data :cmeta cmeta)
    data))

(defmethod merge-in-meta :grimoire.things/namespace
  [{[_ namesp] :coords-suffix :as data}]
  (if-let [cmeta (meta (find-ns (symbol namesp)))]
    (assoc data :cmeta cmeta)
    data))

(defn remove-unreadable [ent-data]
  (reduce #(dissoc-in %1 [:cmeta %2])
          ent-data
          [:ns :test]))

(defn complete-coords
  [{:keys [coords-suffix] :as ent-data} {artifact :name :keys [group version]}]
  (-> ent-data
      (dissoc :coords-suffix)
      (assoc :coords (into [group artifact version] coords-suffix))))

(defn ensure-exists [dir-path]
  (when-not (.exists dir-path)
    (.mkdirs dir-path)))

(defn coords->path [coords]
  (apply io/file coords))

(defn write-data [data root]
  (let [data-dir (io/file root metadata-dirnm (coords->path (:coords data)))
        data-path (io/file data-dir data-fnm)]
    (ensure-exists data-dir)
    (spit data-path data)
    data-path))

(defn jar-from-project
  "<yet to be filled in>

  tools.namespace supports only one namespace per file. We inherit this
  limitation."
  [{artifact :name :keys [group version root] :as project}]
  (let [out-dir  (io/file root "target" "grenada")
        jar-path (io/file out-dir (str artifact "-" version "-metadata.jar"))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group artifact "pom.xml")
        files
        (->> (find-entities-clj root)
             (map merge-in-meta)
             (map remove-unreadable)
             (map #(complete-coords % project))
             (map #(write-data % root))
             doall)
        files-map (into {} (map (fn [p] [p (jar/relativize-path root p)])
                                files))]
    (ensure-exists out-dir)
    (spit pom-path (pom/make-pom project))
    (jar/make-jar jar-path {:manifest-version "1.0"}
                  (conj files-map [pom-path pom-in-jar]))))
