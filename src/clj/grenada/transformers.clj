(ns grenada.transformers
  (:require [clojure.set :as set]
            [medley.core :as medley]
            [plumbing
             [core :as plumbing :refer [safe-get]]
             [map :as map]]
            [slingshot.slingshot :as slingshot]
            [grenada
             [aspects :as a]
             [bars :as b]
             [things :as t]
             [utils :as gr-utils]]
            [grenada.things
             [def :as things.def]
             [utils :as t-utils]]
            [guten-tag.core :as gt]))

;;;; Universal helpers

(defn- rvector [& args]
  (vec (reverse args)))

(defn- select-all-keys
  ([m ks]
   (select-all-keys m ks nil))
  ([m ks default]
   (let [sub-map (select-keys m ks)]
     (if (= (gr-utils/keyset sub-map) (set ks))
       sub-map
       default))))

(defn- map-some [f & colls]
  (filter some?
          (apply map f colls)))

(defn- dissoc-all-in
  "Requires the last element of KS to be a collection. (dissoc-in …)s all
  elements of this collection from M.

  Example:

  ```clojure
  (dissoc-all-in {:a {:x 1 :y 2 :z 3}} [:a [:x :y]])
  ;; => {:a {:z 3}}}
  ```"
  [m ks]
  (reduce plumbing/dissoc-in
          m
          (let [path (vec (butlast ks))]
            (map #(conj path %) (last ks)))))


;;;; Transformer transformers

(defn apply-if [p f]
  (fn apply-if-infn [x]
    (if (p x)
      (f x)
      x)))


;;;; Transformers for single maps

(defn strip-all [tm]
  (assoc tm :bars {}))

;; Note: You might be wondering about the extra check in the
;;       calling-transformer. lein-grim provides us with Things that have
;;       :arglists even though their :type is :var. Example: in-ns. This is
;;       because its type recognition mechanism is a bit primitive. I don't want
;;       to patch over this inconsistency; it has to be solved at its root.
;;       Therefore, if a Thing as :arglists, but is of :type :var, we just drop
;;       the :arglists information. calling-transformer has to make sure that it
;;       only returns something for Things with Aspects ::a/fn, ::a/macro or
;;       ::a/special.
(def ^:private bar-transformer-for
  "This map contains functions that take a Thing and its ::b/any Bar and return
  a new Bar each (or nil). A function should only be called if at least one of
  the elements in its respective set is contained in the ::b/any Bar. The
  functions expect the ::b/any values to the keys in the set to be not nil."
  {#{:doc}
   (fn doc-transformer [_ any-bar]
     [::b/doc (safe-get any-bar :doc)])

   #{:arglists :forms}
   (fn calling-transformer [tm any-bar]
     (if (seq (set/intersection #{::a/fn ::a/macro ::a/special}
                                (safe-get tm :aspects)))
       [::b/calling (plumbing/lazy-get any-bar :arglists
                                       (safe-get any-bar :forms))]))

   #{}
   (fn access-transformer [tm any-bar]
     (if (t/has-aspect? ::a/var-backed tm)
       [::b/access {:private (get any-bar :private false)
                    :dynamic (get any-bar :dynamic false)}]))

   #{:added :deprecated}
   (fn lifespan-transformer [_ any-bar]
     [::b/lifespan {:added      (get any-bar :added)
                    :deprecated (get any-bar :deprecated)}])

   #{:file :line}
   (fn source-location-transformer [_ any-bar]
     (if-let [m (select-all-keys any-bar #{:file :line})]
       [::b/source-location m]))

   #{:author}
   (fn author-transformer [tm any-bar]
     (if (t/has-aspect? ::t/namespace tm)
       [::b/author (safe-get any-bar :author)]))})

;;; Conditions and handlers for specify-cmeta-any. See also grey.core.

(defn- apply-bar-transformer [[any-keys bar-transformer] tm any-bar]
  (if (seq (set/intersection any-keys (gr-utils/keyset any-bar)))
    (bar-transformer tm any-bar)))

(gt/deftag missing-bar-type-defs
  "Condition indicating that specify-cmeta-any didn't receive definitions for
  all Cmeta Bars. BAR-KEYS contains the keys for the Bar types whose definitions
  were missing."
  [bar-keys])

(declare specify-cmeta-any)

(defn- skip-bars
  "Causes the Bars for which there is no definition simply not to be attached to
  TM."
  [etm extra-def-for-bar-type tm]
  (->> (dissoc-all-in tm [:bars ::b/any (safe-get etm :bar-keys)])
       (specify-cmeta-any extra-def-for-bar-type)))

(defn- attach-anyway
  "Attaches the Bars for which there is no definition to TM without validating
  them."
  [etm extra-def-for-bar-type tm]
  (specify-cmeta-any (into extra-def-for-bar-type
                           (map #(vector % (things.def/blank-bar-type-def %))
                                (t-utils/safe-get etm :bar-keys)))
                     tm))

(defn specify-cmeta-any
  "If TM has a :grenada.bars/any Bar containing Cmetadata (as extracted by
  lein-grim), return a new Thing with the data from the Any Bar decomposed into
  Bars with more meaningful types.

  This function tries its best to extract meaning from the unspecified input
  data. However, the mantra is: if in doubt, leave it out. – We don't want to
  compromise meaningfulness.

  If the :grenada.bars/any Bar contains a :grenada.cmeta/bars entry, those Bars
  are also added to TM, provided that grenada.bars/def-for-bar-type or
  extra-def-for-bar-type contain definitions for them.

  Available handlers: :skip, :attach-anyway"
  {:grey/handlers {:skip skip-bars
                   :attach-anyway attach-anyway}}
  ([tm] (specify-cmeta-any {} tm))
  ([extra-def-for-bar-type tm]
   (if-let [any-bar-with-nils (get-in tm [:bars ::b/any])]
     (let [any-bar (medley/remove-vals nil? any-bar-with-nils)
           new-bars (map-some #(apply-bar-transformer % tm any-bar)
                              bar-transformer-for)
           cmeta-bars (get any-bar :grenada.cmeta/bars)
           all-def-for-bar-type (merge b/def-for-bar-type
                                       extra-def-for-bar-type)
           missing-defs (set/difference (gr-utils/keyset cmeta-bars)
                                        (gr-utils/keyset all-def-for-bar-type))]
       (when (seq missing-defs)
         (slingshot/throw+ (->missing-bar-type-defs missing-defs)))
       (->> tm
            (t/detach-bar ::b/any)
            (t/attach-bars b/def-for-bar-type new-bars)))
     tm)))

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
