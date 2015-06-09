(ns grenada-lib.util
  "Miscellaneous utilities."
  (:require plumbing.core))

(defmacro fnk*
  "Shortens fnks that just apply some other function to their arguments.

    (fnk [x] (inc x)) → (fnk* [x] inc)
    (fnk [x] (+ 4 x)) → (fnk* [x] (+ 4))"
  [symv form]
  `(plumbing.core/fnk [~@symv]
                      ~(if (list? form)
                         `(~@form ~@symv)
                         `(~form ~@symv))))
