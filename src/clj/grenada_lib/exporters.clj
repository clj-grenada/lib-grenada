(ns grenada-lib.exporters
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [plumbing.core :refer [safe-get]]
            [grenada-lib.config :refer [config]]))

;;;; Miscellaneous helpers

(defn- coords->path [coords]
  (apply io/file (map grimoire.util/munge coords)))

(defn- prn-spit [path x]
  (with-open [writer (io/writer path)]
    (binding [*out* writer] (prn x))))

;;;; Hierarchical filesystem exporter

(defn- exp-map-fs-hier [m out-dir]
  (let [data-dir (io/file out-dir (coords->path (:coords m)))
        data-path (io/file data-dir (safe-get config :datafile-name))]
    (io/make-parents data-path)
    (prn-spit data-path m)
    data-path))

(defn fs-hier [data out-dir]
  (doseq [m data]
    (exp-map-fs-hier m out-dir)))


;;;; Flat filesystem exporter

(defn fs-flat [data out-file]
  (prn-spit out-file data))

;; TODO: Always start a new line for the extensions map as well as existing
;;       extensions. Example:
;;
;;         {:name …
;;          :coords …
;;          :level …
;;          :extensions
;;          {:extension1
;;           …
;;           :extension2
;;           …
;;           …}}
;;
;;       (RM 2015-06-21)
(defn pprint-fs-flat
  "

  Defaults to not overwriting, since something pprint-ed is likely to be edited
  as external metadata and the user would be very sad if she accidentally had
  her wonderful hand-crafted examples wiped out."
  [data out-file & [?overwrite]]
  (when (and (not ?overwrite) (.exists (io/as-file out-file)))
    (throw (IllegalStateException.
             (str out-file " already exists. You might not want me to"
                  " overwrite it."))))
  (with-open [w (io/writer out-file)]
    (pprint/pprint data w)))
