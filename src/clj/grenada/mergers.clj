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

(defn- merge-nonconflict [m1 m2]
  (t-utils/merge-with-key
    (fn [k v1 v2]
      (if (= v1 v2)
        v1
        (throw (IllegalArgumentException.
                 (str "Different values " v1 " and " v2 " supplied for key " k
                      ". Refusing to merge.")))))
    m1 m2))


;;;; The simple merge and its helpers

(defn- merge-fn-from [handler-for]
  (fn [k v1 v2]
    (if-let [handle (get handler-for k)]
      (handle v1 v2)
      (throw (IllegalArgumentException.
               (str "Different values " v1 " and " v2 " supplied for extension"
                    " " k " and no handler. Refusing to merge."))))))

(defn- simple-merge-2 [& [ext-handlers]]
  (fn [m1 m2]
    (let [[[ext1 other1] [ext2 other2]]
          (map #(separate-key % :extensions) [m1 m2])]
      (-> (merge-nonconflict other1 other2)
          (plumbing/assoc-when
            :extensions
            (map/merge-with-key (merge-fn-from ext-handlers) ext1 ext2))))))

;; REFACTOR: Might explicate that the final vals is also a converter. (RM
;;           2015-06-23)
(defn simple-merge [& [ext-handlers]]
  (fn [ms1 ms2]
    (->> [ms1 ms2]
         (map gr-conv/to-mapping)
         (apply merge-with (simple-merge-2 ext-handlers))
         vals)))
