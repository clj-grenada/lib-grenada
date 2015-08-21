(ns grenada.utils.jar
  "A rather shapeless namespace for procedures concering Datadoc JARs."
  (:require [clojure.java.io :as io]
            [darkestperu.jar :as jar]
            [grenada
             [config :refer [config]]
             [schemas :as schemas]]
            [leiningen.pom :as pom]
            [plumbing.core :refer [safe-get]]
            [schema.core :as s]))

;;; jar-from-entries-map is used by a procedure in grenada.exporters and by one
;;; in grenada.postprocessors. Those procedures do belong there and
;;; jar-from-entries-map is not more closely associated with any one of them.
;;; Therefore I have to put it in a separate, though small, namespace.

(defn jar-name
  "Returns the filename for a Datadoc JAR for ARTIFACT and VERSION."
  [artifact version]
  {:pre [artifact version]}
  (io/file (format "%s-%s-%s.jar"
                   artifact
                   version
                   (safe-get config :classifier))))

;; DE-HACK 2.0.0: Separate the coords-out from additional POM entries. (RM
;;         2015-08-21)
(s/defn jar-from-entries-map
  "Creates a Datadoc JAR containing data as specified by ENTRIES-MAP and a
  pom.xml in OUT-DIR.

  ENTRIES-MAP is the same as the FILES-MAP argument to darkestperu.jar/jar.

  Not part of the public API."
  [entries-map out-dir {:keys [group artifact version]
                        :as coords-out} :- schemas/JarCoordsWithDescr]
  (let [jar-path (io/file out-dir (jar-name artifact version))
        pom-path (io/file out-dir "pom.xml")
        pom-in-jar (io/file "META-INF" "maven" group artifact "pom.xml")]
    (io/make-parents pom-path) ; Also takes care of parents for JAR file.
    (spit pom-path (-> coords-out
                       (assoc :name artifact) ; Because we're going back to lein
                       pom/make-pom))
    (jar/make-jar jar-path {:manifest-version "1.0"}
                  (conj entries-map [pom-in-jar (jar/->file-entry pom-path)]))))

