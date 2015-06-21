(ns grenada-lib.transformers
  (:require [plumbing.core :as plumbing :refer [safe-get]]))

;;;; Helpers

(defn rvector [& args]
  (vec (reverse args)))

(defn get-namespace [m]
  {:pre [(= :grimoire.things/def (safe-get m :level))]}
  (plumbing/safe-get-in m [:coords 4]))


;;;; Transformers

(defn strip-all [m]
  (select-keys m #{:name :coords :level :kind}))

;; This could be a good one for programming golf, I guess.
(defn sensible-key-order [k1 k2]
  (let [order [:name :level :kind :coords :extensions]
        index-for (into {} (map-indexed rvector order))
        higher (count order)]
    (compare (get index-for k1 higher)
             (get index-for k2 higher))))

;; With an external library, we could use an ordered map, to the same end.
(defn sort-keys [m]
  (into (sorted-map-by sensible-key-order) m))

;;  - Make all maps sorted maps with key order [:name :level :kind :coords
;;    :extensions].
;;  - Order the whole as ns, def, def, def, …, ns, def, def, …, ….
;;
;; TODO: Support higher levels than namespace. (RM 2015-06-20)
(defn reorder-for-output [ms]
  (let [sorted-m-ms (map sort-keys ms)
        nsmaps (sort-by :name (filter #(= :grimoire.things/namespace (:level %))
                                      sorted-m-ms))
        defmaps (filter #(= :grimoire.things/def (:level %)) sorted-m-ms)
        defmaps-by-ns (group-by get-namespace defmaps)]
    (plumbing/aconcat
      (map #(cons % (sort-by :name (safe-get defmaps-by-ns
                                             (safe-get % :name))))
           nsmaps))))

(defn add-ext [k v m]
  (assoc-in m [:extensions k] v))
