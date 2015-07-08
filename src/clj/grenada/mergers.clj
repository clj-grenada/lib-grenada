(ns grenada.mergers
  (:require [guten-tag.core :as gt]
            [grenada.things.utils :as t-utils]
            [grenada.converters :as gr-conv]
            [plumbing.map :as map]))

;;; Note that these functions don't contain much any handling of nils or
;;; non-existent map entries. This is because the functions they're using have
;;; quite fortunate semantics in those cases. I.e., they do the right thing.
;;; Make sure that when you're changing the code around here, the functions that
;;; you're using do the right thing, too.

;;;; Universal helpers

(defn- separate-key [m k]
  [(get m k) (dissoc m k)])

(defn empty*?
  "Returns true if X is something that can hold multiple values, but is empty.
  Something that can hold multiple items is something that satisfies coll? or
  seq?.

  I exclude nil from satisfying empty*?, because it's somehow not in line with
  the usual requirements for the :extensions and :cmeta entries. However, I'm
  not so sure about whether this is necessary, so if it bothers you, we can
  discuss it."
  [x]
  (and (some? x)
       (or (coll? x) (seq? x))
       (empty? x)))

(defn- merge-nonconflict
  "Merges Tmaps TM1 and TM2 in a way that conflicts cause exceptions. Conflicts
  are all cases where the same key occurs in both Tmaps with the following
  exceptions:

  - The values to the key are the same in both Tmaps.

  - One of the values is empty and the other is not. In this case we take the
    non-empty value into the result. Emptiness is that of empty*?."
  [tm1 tm2]
  (t-utils/merge-with-key
    (fn merge-fn [k v1 v2]
      (cond
        (= v1 v2)
        v1

        (and (empty*? v1) (not (empty*? v2)))
        v2

        (and (empty*? v2) (not (empty*? v1)))
        v1

        :default
        (throw (IllegalArgumentException.
                 (str "Different values " v1 " and " v2 " supplied for"
                      " key " k ". Refusing to merge.")))))
    tm1 tm2))


;;;; The simple merge and its helpers

(defn- merge-fn-from [handler-for]
  (fn merge-fn-from-infn [k v1 v2]
    (if-let [handle (get handler-for k)]
      (handle v1 v2)
      (throw (IllegalArgumentException.
               (str "Different values " v1 " and " v2 " supplied for extension"
                    " " k " and no handler. Refusing to merge."))))))

(defn- simple-merge-2 [& [ext-handlers]]
  (fn simple-merge-2-infn [m1 m2]
    (let [[[ext1 other1] [ext2 other2]]
          (map #(separate-key % :extensions) [m1 m2])
          merged-others (merge-nonconflict other1 other2)
          merged-exts (map/merge-with-key
                        (merge-fn-from ext-handlers)
                        ext1 ext2)]
      (assoc merged-others :extensions merged-exts))))

;; REFACTOR: Might explicate that the final vals is also a converter. (RM
;;           2015-06-23)
(defn simple-merge [& [ext-handlers]]
  (fn simple-merge-infn [ms1 ms2]
    (->> [ms1 ms2]
         (map gr-conv/to-mapping)
         (apply merge-with (simple-merge-2 ext-handlers))
         vals)))
