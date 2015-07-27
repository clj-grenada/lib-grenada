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

(def fn-def
  "Definition of the Aspect `::fn`.

  ## Semantics

  A Thing with the Aspect `::fn` describes a **concrete Clojure fn**, that is,
  an object that satisfies `fn?`.

  ## Prerequisites

  Only `:grenada.things/var-backed` (implying `:grenada.things/find`) can be
  `::fn`.

  ## Canonical name

  See clj::grenada.aspects/var-backed-def.

  ## Remarks

  Somewhat confusingly, Finds with the Aspect `::fn` can have multiple
  **different names**. For example, see this:

  ```clojure
  user=> (def bark (fn miaow [] (throw (Exception. \"🐷 Grunt.\"))))
  #'user/bark
  user=> (bark)

  Exception 🐷 Grunt.  user/miaow (NO_SOURCE_FILE:1)
  user=>                 ; ‾‾‾‾‾
  ```
  "
  (things.def/map->aspect
    {:name ::fn
     :prereqs-pred (fn fn-prereqs-fulfilled? [aspects]
                     (set/subset? #{::t/find ::var-backed} aspects))
     :name-pred string?}))

(def macro-def
  "Definition of the Aspect `::macro`.

  ## Semantics

  A Thing with the Aspect `::macro` describes a **concrete Clojure macro**, that
  is, an object for which `(:macro (meta (var <object>))` evaluates to `true`.

  ## Prerequisites

  Only `:grenada.things/var-backed` (implying `:grenada.things/find`) can be
  `::macro`.

  ## Canonical name

  See clj::grenada.aspects/var-backed-def."
  (things.def/map->aspect
    {:name ::macro
     :prereqs-pred (fn macro-prereqs-fulfilled? [aspects]
                     (set/subset? #{::t/find ::var-backed} aspects))
     :name-pred string?}))

(def special-def
  "Definition of the Aspect `::special`.

  ## Semantics

  A Thing with the Aspect `::special` describes a **concrete Clojure special
  form**. What that means is hard to say exactly.

   1. There are concrete things t that *are* accessible through a **var**, for
      example `let`. In that case `(:special-form (meta (var t)))` returns
      `true`. Also their source code is available through
      clj::clojure.core/source. They are included in the [official listing of
      special forms](http://clojure.org/special_forms).

   2. There are concrete things that are **not backed by a var** and whose
      source code is not available, but which, too, are listed as special forms.
      For example, `def`.

   3. There are concrete things that are not backed by a var and **not listed**
      as a special form. One I know of is `let*`, but there might be more.

  The first two kinds are `::special`. The third is not considered by Grenada. –
  It's not considered by anyone else either.

  Concrete finds of the second kind are not interned in any namespace. In order
  to maintain consistency with other tools, the Aspect `::special` defines the
  **namespace coordinate** of those Things to be `clojure.core`.

  ## Prequisites

  A Thing already has to be a `:grenada.things/find` in order for `::special` to
  be attached.

  ## Canonical name

  For the canonical name for special forms of the first kind, see
  clj::grenada.aspects/var-backed-def. The canonical name of a special form of
  the second kind is the `str`ing representation of the symbol by which you can
  use it as the operator in a Clojure [call](http://clojure.org/evaluation).

  ## Remarks

  You may attach the Aspect `::var-backed` to the Things representing special
  forms of the first kind.

  All this is a bit fuzzy and confused. Suggestions for improvement are welcome.
  However, there are not so many special forms and their number is fairly
  constant, so I think we'll just cope with it."
  (things.def/map->aspect
   {:name ::special
    :prereqs-pred (fn special-prereqs-fulfilled? [aspects]
                    (contains? aspects ::t/find))
    :name-pred string?}))


;;;; Public API

(def def-for-aspect
  "A map from Aspect keywords to definitions of Aspects in this namespace."
  (things.def/map-from-defs #{var-backed-def fn-def macro-def special-def}))
