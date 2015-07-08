(ns grenada.transformers
  (:require [plumbing.core :as plumbing :refer [safe-get]]
            [grenada.util :as gr-util]
            [grenada.things :as t]
            [grenada.things.utils :as t-utils]
            [guten-tag.core :as gt]))

;;;; Universal helpers

(defn- rvector [& args]
  (vec (reverse args)))


;;;; Transformer transformers

(defn apply-if [p f]
  (fn apply-if-infn [x]
    (if (p x)
      (f x)
      x)))


;;;; Transformers for single maps

(defn strip-all [tm]
  (as-> tm x
    (assoc x :extensions {})
    (if (t/has-cmeta? x)
      (assoc x :cmeta {}))))

(defn add-ext [k v]
  (fn [m]
    (assoc-in m [:extensions k] v)))

(defn transform-ext
  "The function F will be passed both the complete metadata map M and the
  extension value. It is expected to return a new extension _entry_, which will
  replace the old one. This gives it freedom (to read all existent data),
  convenience (not to have to find the right extension entry itself) and safety
  (not to mess up the whole metadata map). If you don't believe in freedom,
  convenience and safety, please contact me."
  [k f]
  (fn transform-ext-infn [m]
    (t-utils/conj (gr-util/dissoc-in* m [:extensions k])
                  (f m (plumbing/safe-get-in m [:extensions k])))))


;;;; Transformers for whole collections

;; This could be a good one for programming golf, I guess.
(defn- sensible-key-order [k1 k2]
  (let [order [:name :coords :extensions :cmeta]
        index-for (into {} (map-indexed rvector order))
        higher (count order)]
    (compare (get index-for k1 higher)
             (get index-for k2 higher))))

;; With an external library, we could use an ordered map, to the same end.
(defn- sort-keys [[t m :as tm]]
  {:pre [(gt/tagged? tm)]}
  (gt/->ATaggedVal t
                   (into (sorted-map-by sensible-key-order) m)))

;; TODO: Support higher levels than namespace. (RM 2015-06-20)
(defn reorder-for-output [ms]
  (let [sorted-m-ms (map sort-keys ms)
        nsmaps (sort-by :name
                        (filter t/namespace? sorted-m-ms))
        defmaps (filter t/def? sorted-m-ms)
        defmaps-by-ns (group-by t/namespace-coord defmaps)]
    (plumbing/aconcat
      (for [n nsmaps
            :let [ds (as-> n x
                       (safe-get x :name)
                       (safe-get defmaps-by-ns x)
                       (sort-by :name x))]]
        (cons n ds)))))
