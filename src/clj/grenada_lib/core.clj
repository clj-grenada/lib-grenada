(ns grenada-lib.core
  (:require [clojure.java.io :as io]))

(def metadata-dirnm "grenada-data")
(def data-fnm "data.edn")

(defn ensure-exists [dir-path]
  (when-not (.exists dir-path)
    (.mkdirs dir-path)))

(defn extract-meta [namesp]
  (let [cmeta (meta namesp)]
    {:name (str namesp)
     :level :grimoire.things/namespace
     :cmeta cmeta}))

(defn extract-and-write [{artifact :name :keys [group version root]} namesp]
  (let [ns-meta (extract-meta namesp)
        ns-meta-dir (io/file root metadata-dirnm group artifact version
                             (str namesp))
        ns-meta-path (io/file ns-meta-dir data-fnm)]
    (ensure-exists ns-meta-dir)
    (spit ns-meta-path ns-meta)))
