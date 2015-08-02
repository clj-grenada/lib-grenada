(ns grenada.exporters.pretty
  "Pretty-printing exporters. Uses fipp 0.6.2, therefore you can't use
it in Clojure ≦ 1.6.0."
  (:require [clojure.java.io :as io]
            [fipp edn visit]
            [grenada.things :as t]))

;;; This is a .cljc file, so that Clojure ≦ 1.6.0 won't even see it.

;; TODO: Connect this to what is defined in guten-tag. – Currently we're just
;;       copying the 'g/t and when it changes, we have a problem.
(defn print-ataggedval [edn-pr [t m]]
  (fipp.visit/visit-tagged edn-pr {:tag 'g/t
                                   :form [t m]}))

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
