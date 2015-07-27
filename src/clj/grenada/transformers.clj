(ns grenada.transformers
  (:require [plumbing.core :as plumbing :refer [safe-get]]
            [grenada.utils :as gr-utils]
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
  (assoc tm :bars {}))


;;;; Transformers for whole collections

;; This could be a good one for programming golf, I guess.
(defn- sensible-key-order [k1 k2]
  (let [order [:coords :aspects :bars]
        index-for (into {} (map-indexed rvector order))
        higher (count order)]
    (compare (get index-for k1 higher)
             (get index-for k2 higher))))

;; With an external library, we could use an ordered map, to the same end.
(defn- sort-keys [[t m :as tm]]
  {:pre [(gt/tagged? tm)]}
  (gt/->ATaggedVal t
                   (into (sorted-map-by sensible-key-order) m)))
