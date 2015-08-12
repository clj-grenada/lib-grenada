(ns grenada.utils.jar
  "A rather shapeless namespace for procedures concering Datadoc JARs."
  (:require [clojure.java.io :as io]
            [darkestperu.jar :as jar]
            [grenada.config :refer [config]]
            [leiningen.pom :as pom]
            [plumbing.core :refer [safe-get]]))

;;; jar-from-entries-map is used by a procedure in grenada.exporters and by one
;;; in grenada.postprocessors. Those procedures do belong there and
;;; jar-from-entries-map is not more closely associated with any one of them.
;;; Therefore I have to put it in a separate, though small, namespace.

(defn jar-name [artifact version]
  {:pre [artifact version]}
  (io/file (format "%s-%s-%s.jar"
                   artifact
                   version
                   (safe-get config :classifier))))

(defn jar-from-entries-map
  "

  Not part of the public API."
  [entries-map out-dir {:keys [group artifact version] :as coords-out}]
  (let [jar-path (io/file out-dir (jar-name artifact version))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group artifact "pom.xml")]
    (io/make-parents pom-path) ; Also takes care of parents for JAR file.
    (spit pom-path (-> coords-out
                       (assoc :name artifact) ; Because we're going back to lein
                       pom/make-pom))
    (jar/make-jar jar-path {:manifest-version "1.0"}
                  (conj entries-map [pom-in-jar (jar/->file-entry pom-path)]))))

