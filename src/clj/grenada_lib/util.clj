(ns grenada-lib.util
  "Miscellaneous utilities."
  (:require plumbing.core
            clojure.core.contracts
            trammel.provide))

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

(defmacro defconstrainedfn*
  "Combines the functionality of defconstrainedfn and provide/contracts.

  With trammel.core/defconstrainedfn you can define a function and define the
  contracts it has to fulfill in one place. With trammel.provide/contracts you
  can define the function and the contract somewhere and later require the
  function to fulfill the contract.

  What's missing is the possibility to define a contract somewhere and then put
  the definition of a function and a specification of the contracts it has to
  fulfill in one place. You'd have to write the (defn …) and then one or more
  (trammel.provide/contracts …) afterwards. That defies the secondary purpose of
  contracts as documentation.

  This macro provides that functionality. Example:

    (require '[trammel.core :as trammel])

    (trammel/defcontract integer->number
      \"Takes integers to numbers.\"
      [& args] [(every? integer? args) => number?])
    (trammel/defcontract always-more
      \"We want the result to be more.\"
      [x & args] [=> (> % x)])

    (defconstrainedfn* plus [integer->number always-more]
      [m n]
      (+ m n))

  Apart from having to provide the contracts vector, you can use the full syntax
  of (defn …)."
  [& forms]
  (let [fun-sym#        (first forms)
        contracts-syms# (second forms)
        defn-forms#     (remove-nth 1 forms)
        doc-str#        (str "Constraint for " fun-sym#)
        provides#
        (map (fn [cs] `(trammel.provide/contracts [~fun-sym# ~doc-str# ~cs]))
             contracts-syms#)]
    `(do
       (defn ~@defn-forms#)
       ~@provides#)))
