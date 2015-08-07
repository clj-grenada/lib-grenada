(ns grenada.bars
  "Defines a few Bar types that are shipped with lib-grenada."
  {:grenada.cmeta/bars {:doro.bars/markup-all :common-mark}}
  (:require [grenada.things.def :as things.def]
            [schema.core :as s]))

(def any-def
  "Definition of the Bar type `::any`.

  ## Model

  This Bar allows attaching arbitrary key-value data to a Thing. It is thought
  for intermediate processing steps. As it provides no guarantees about its
  contents, it should only be present within short distance of consumption, i.
  e. not in final Grenada artifacts.

  ## Prerequisites

  None. – Can be attached to any Thing."
  (things.def/map->bar-type {:name ::any
                             :schema {s/Keyword s/Any}}))

(def doc-def
  "Definition of the Bar type `::doc`.

  ## Model

  A Bar of this type holds the **doc string** attached to a Thing in the normal
  Clojure way. If a concrete thing doesn't support attaching doc strings in the
  normal Clojure way (eg. deftypes, defmethods), Bars of this type may be used
  as a **substitute**.

  ## Prerequisites

  Can only be attached to Namespaces and Finds.

  ## Remarks

  This Bar type is really only for standard Clojure docstrings and for working
  around the lack of docstring support. If you want to **supplement** docstrings
  of Namespaces and Finds or want to add documentation to Groups, Artifacts,
  Versions or Platforms, please use a different Bar type."
  (things.def/map->bar-type {:name ::doc
                             :aspect-prereqs-pred
                             (fn doc-def-aspect-prereqs-fulfilled? [aspects]
                               (some #(t/below-incl ::t/namespace %) aspects))
                             :valid-pred string?}))

(def lifespan-def
  "Definition of the Bar type `::lifespan`.

  ## Model

  A Bar of this type holds information on when a Find was **added** and,
  possibly, **deprecated**.

   - `:added` is the version in which a Thing first occured in the project.

   - `:deprecated` is the version in which a Thing was first deprecated. If it
     is `nil`, the Thing is not deprecated.

  ## Prerequisites

  Can only be attached to Finds."
  (things.def/map->bar-type {:name ::lifespan
                             :aspect-prereqs-pred
                             (fn lifespan-def-asp-prereqs-fulfilled? [aspects]
                               (contains? aspects ::t/find))
                             :schema {:added s/Str
                                      :deprecated (s/either s/Str nil)}}))

(def def-for-bar-type
  (things.def/map-from-defs #{any-def doc-def lifespan-def}))
