(ns grenada.things.utils
  "Wrappers around well-known functions in order to make them work with
  guten-tag.core/ATaggedVals."
  (:refer-clojure :exclude [select-keys conj])
  (:require [clojure.core :as clj]
            [plumbing.map :as map]
            [guten-tag.core :as gt]))

;;; TODO: See if we can ship these with guten-tag or as a supplementary library.
;;;       (RM 2015-07-08)

(defn select-keys [[t m :as tm] keyseq]
  {:pre [(gt/tagged? tm)]}
  (gt/->ATaggedVal t (clj/select-keys m keyseq)))

;; REVIEW: Can we change ATaggedVal so that this is not necessary?
(defn conj
  ([]
   (throw (UnsupportedOperationException. "Arity 0 of conj is not supported.")))
  ([[t m :as tm] & xs]
   (gt/->ATaggedVal t (apply clj/conj m xs))))

(defn merge-with-key [f & tms]
  (let [ts (map gt/tag tms)
        ms (map gt/val tms)]
    (assert (reduce = ts))
    (gt/->ATaggedVal (first ts) (apply map/merge-with-key f ms))))
