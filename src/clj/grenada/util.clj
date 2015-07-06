(ns grenada.util
  "Miscellaneous utilities."
  (:require plumbing.core))

;;; TODO: Rename this namespace to grenada.utils in order to make it consistent
;;;       with what I usually do. (RM 2015-06-27)

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

(defn remove-nth
  "Returns COLL with the item with offset N removed.

  Example:
    user=> (remove-nth [0 1 2 3 4 5] 3)
    (0 1 2 4 5)"
  [n coll]
  (concat (take n coll) (drop (inc n) coll)))
