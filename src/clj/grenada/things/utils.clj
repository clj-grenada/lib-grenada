(ns grenada.things.utils
  "Wrappers around well-known functions in order to make them work with
  guten-tag.core/ATaggedVals."
  (:refer-clojure :exclude [select-keys conj])
  (:require [clojure.core :as clj]
            grenada.utils
            [plumbing
             [core :as plumbing]
             [map :as map]]
            [guten-tag.core :as gt]))

;;; TODO: See if we can ship these with guten-tag or as a supplementary library.
;;;       (RM 2015-07-08)
;;; MAYBE TODO: Concile these with grenada.guten-tag.more. (RM 2015-07-24)

(defn select-keys
  "Like clj::clojure.core/select-keys, but for ATaggedVals."
  [[t m :as tm] keyseq]
  {:pre [(gt/tagged? tm)]}
  (gt/->ATaggedVal t (clj/select-keys m keyseq)))

;; REVIEW: Can we change ATaggedVal so that this is not necessary?
(defn conj
  "Like Same as clj::clojure.core/conj, but for ATaggedVals."
  ([]
   (throw (UnsupportedOperationException. "Arity 0 of conj is not supported.")))
  ([[t m :as tm] & xs]
   (gt/->ATaggedVal t (apply clj/conj m xs))))

(defn merge-with-key
  "Like Same as clj::plumbing.map/merge-with-key but for ATaggedVals."
  [f & tms]
  (let [ts (map gt/tag tms)
        ms (map gt/val tms)]
    (assert (reduce = ts) "not all tags are equal!")
    (gt/->ATaggedVal (first ts) (apply map/merge-with-key f ms))))

(defn fmap
  "Applies F to the val part of an ATaggedVal and returns an ATaggedVal with the
  same tag and the result of the application.

  Something like the morphism morphism (metamorphism?) of the ATaggedVal
  functor."
  [f [t v]]
  (gt/->ATaggedVal t (f v)))

(defn assoc-when
  "Like clj::plumbing.core/assoc-when, but for ATaggedVals."
  [tv k v]
  (fmap #(plumbing/assoc-when % k v) tv))

(defn safe-get
  "Like clj::plumbing.core/safe-get, but for ATaggedVals."
  [[_ v] k]
  (plumbing/safe-get v k))

(defn safe-get-in
  "Like clj::plumbing.core/safe-get, but for ATaggedVals."
  [[_ v] ks]
  (plumbing/safe-get-in v ks))

(defn safe-select-keys
  "Like clj::plumbing.map/safe-select-keys, but for ATaggedVals."
  [[_ v] ks]
  (map/safe-select-keys v ks))
