(ns grenada.aspects
  "Defines the core Aspects provided by Grenada.

  The main Aspects are not defined here, but in `grenada.things`, though."
  {:grenada.cmeta/bars {:doro.bars/markup-all :common-mark}}
  (:require [clojure.set :as set]
            [grenada
             [things :as t]]
            [grenada.things.def :as things.def]))

(def var-backed-def
  "Definition of the Aspect `::var-backed`.

  ## Semantics

  A Thing with the Aspect `::var-backed` describes a concrete Clojure object
  that has something to do with a var interned in a namespace. Typical
  **examples** are fns and plain defs, which are only stored in vars. But
  protocols are also `::var-backed`, although defining a protocol results in the
  creation of both a var and a Java class.

  ## Prerequisites

  A Thing already has to be a `:grenada.things/find` in order for `::var-backed`
  to be attached.

  ## Canonical name

  Be v a var interned in some namespace under the symbol `s`. v was interned
  when some concrete thing x was defined. X is the `:a/var-backed` Find that
  contains data about x. `(str s)` is the name of X.

  ```
  (ns name.space)
                       (ns-interns (find-ns 'name.space))
                       ;=> {… …
  (def… … …)   ---->        s v
                            … …}
  ```"
  (things.def/map->aspect
    {:name ::var-backed
     :prereqs-pred (fn var-backed-prereqs-fulfilled? [aspects]
                     (contains? aspects ::t/find))
     :name-pred string?}))


;;;; Public API

(def def-for-aspect
  "A map from Aspect keywords to definitions of Aspects in this namespace."
  (things.def/map-from-defs #{var-backed-def}))
