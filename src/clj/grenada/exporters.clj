(ns grenada.exporters
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            fipp.edn
            fipp.visit
            [plumbing.core :as plumbing :refer [safe-get]]
            grimoire.util
            [grenada.config :refer [config]]
            [grenada.things :as t]))

;;;; Miscellaneous helpers

(defn- coords->path
  "Returns a File with a relative path from the given coords. The result can be
  used to store a Thing on disk, for example.

  Note that Grimoire only munges the Def coordinate (Find in Grenada), whereas
  this munges all. The result are quite ugly paths with version strings looking
  like '1%2E6%2E0'. However, weird characters might occur in almost all
  coordinates, so I don't understand why we should limit munging to the Def
  coordinate."
  [coords]
  (apply io/file (map grimoire.util/munge coords)))


;;;; Printing primitives

(defn prn-spit [path x]
  (with-open [writer (io/writer path)]
    (binding [*out* writer] (prn x))))

;; TODO: Connect this to what is defined in guten-tag. – Currently we're just
;;       copying the 'g/t and when it changes, we have a problem.
(defn print-ataggedval [edn-pr [t m]]
  (fipp.visit/visit-tagged edn-pr {:tag 'g/t
                                   :form [t m]}))


;;;; Hierarchical filesystem exporter

(defn- exp-map-fs-hier [m out-dir]
  (let [data-dir (io/file out-dir (coords->path (safe-get m :coords)))
        data-path (io/file data-dir (safe-get config :datafile-name))]
    (io/make-parents data-path)
    (prn-spit data-path m)
    data-path))

(defn fs-hier [data out-dir]
  (doseq [m data]
    (exp-map-fs-hier m out-dir)))



;;;; Flat filesystem exporters

(defn fs-flat [data out-file]
  (prn-spit out-file data))

;; TODO: Always start a new line for the Bars map as well as existing Bars.
;;       Example:
;;
;;         {:coords …
;;          :aspects …
;;          :bars
;;          {:bar1
;;           …
;;           :bar2
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
    (binding [*out* w]
      (fipp.edn/pprint data {:symbols {::t/thing print-ataggedval}}))))
                             ; In project.clj I menat this :symbols entry.
