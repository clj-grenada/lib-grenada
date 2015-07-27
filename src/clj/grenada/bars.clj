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

(def def-for-bar-type
  (things.def/map-from-defs #{any-def}))
