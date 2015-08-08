(ns grenada.utils
  "Miscellaneous utilities."
  (:require [plumbing.core :refer [safe-get]]))

(defn warn [& args]
  (binding [*out* *err*] (apply println "WARNING! "args)))

(defmacro fnk*
  "Shortens fnks that just apply some other function to their arguments.

    (fnk [x] (inc x)) → (fnk* [x] inc)
    (fnk [x] (+ 4 x)) → (fnk* [x] (+ 4))"
  [symv form]
  `(plumbing.core/fnk [~@symv]
                      ~(if (list? form)
                         `(~@form ~@symv)
                         `(~form ~@symv))))

(defn dissoc-in*
  "Like plumbing.core/dissoc-in, but doesn't remove empty maps."
  [m ks]
  (if m
    (if  (<= (count ks) 1)
      (apply dissoc m ks)
      (let [path (butlast ks)
            target-map (get-in m path)
            target-key (last ks)
            dissoced-map (dissoc target-map target-key)]
        (assoc-in m path dissoced-map)))))

(defn remove-nth
  "Returns COLL with the item with offset N removed.

  Example:
    user=> (remove-nth [0 1 2 3 4 5] 3)
    (0 1 2 4 5)"
  [n coll]
  (concat (take n coll) (drop (inc n) coll)))

(defn safe-select-keys
  "Analogy: get:safe-get :: select-keys:safe-select-keys"
  [m ks]
  (plumbing.core/map-from-keys #(safe-get m %) ks))

(defn keyset [m]
  (set (keys m)))
