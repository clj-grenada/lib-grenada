(ns grenada.bars
  "Defines a few Bar types that are shipped with lib-grenada.

  Most of them are made to accommodate the **Cmetadata** added to concrete
  things in clojure.core, which are likely being imitated by other code. The
  descriptions of **semantics** I give are my own interpretations of the usually
  sparse information provided by the Clojure documentation.

  **No Bars** are defined for the following Cmetadata entries commonly found on
  Clojure things:

   - `:name`, `:ns` – Already contained in the coordinates.
   - `:macro`       – Already conveyed by an Aspect.
   - `:static`      – This is the default since Clojure 1.very-early.
   - `:tag`         – Might be an Object and therefore difficult. Not so
                      important for humans anyway. (Correct me if I'm wrong.)
   - `:test`        – Doesn't occur in `clojure.core` and right now I don't know
                      how to handle it. Also not so important."
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
                             (fn doc-aspect-prereqs-fulfilled? [aspects]
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
                             :aspect-prereqs-pred #(contains? % ::t/find)
                             :schema {:added s/Str
                                      :deprecated (s/either s/Str nil)}}))

(def source-location-def
  "Definition of the Bar type `::source-location`.

  ## Model

  A Bar of this type holds information about where a Find is located in **source
  code**.

   - `:file` is the file the Find can be found in. This corresponds to the
     `:file` Cmetadata [attached](http://clojure.org/special_forms) by the
     Clojure compiler. It is a relative path and might therefore not be easy to
     interpret. More helpful Bars might be able to provide URLs.

   - `:line` is the line in the `:file` where the definition of the Find starts.

  ## Prerequisites

  Can only be attached to Finds."
  (things.def/map->bar-type {:name ::source-location
                             :aspect-prereqs-pred #(contains? % ::t/find)
                             :schema {:file s/Str
                                      :line s/Int}}))

(def def-for-bar-type
  (things.def/map-from-defs #{any-def
                              doc-def
                              lifespan-def
                              source-location-def}))
