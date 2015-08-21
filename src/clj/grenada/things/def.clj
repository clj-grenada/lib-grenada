(ns grenada.things.def
  "Functions and other things constituting the foundation for defining Things."
  (:require [plumbing.core :as plumbing :refer [safe-get]]
            [guten-tag.core :as gt]
            [grenada.guten-tag.more :as gt-more]
            [schema
             [core :as s]
             [macros :refer [assert!]]]
            [grenada.schemas :as schemas]))

;;;; Some auxiliary definitions concerning Aspects definitions

(s/defschema AspectSchema
  "Schema for the programmatic part of Aspect definitions."
  {:name schemas/NSQKeyword
   :prereqs-pred schemas/Fn
   :name-pred schemas/Fn})

(def aspect-defaults
  "Default values for Aspect definitions."
  {:prereqs-pred (fn [_] true)
   :name-pred (fn [_] true)})


;;;; Tag type definitions for two kinds of Aspects definitions

(gt-more/deftag+ main-aspect
  "Tmap type for holding the programmatic part of the definition of a Main
  Aspect."
  [name ncoords prereqs-pred name-pred]
  (assoc AspectSchema :ncoords s/Int)
  aspect-defaults)

(gt-more/deftag+ aspect
  "Tmap type for holding the programmatic part of the definition of an ordinary
  Aspect."
  [name prereqs-pred name-pred]
  AspectSchema
  aspect-defaults)


;;;; Definition of Bar type definition

;; TODO: Maybe improve the syntax of deftag+. This here looks a bit ugly. (RM
;;       2015-07-24)
(gt-more/deftag+ bar-type
  "Tmap type for holding the programmatic part of the definition of a Bar type. "
  [name aspect-prereqs-pred bar-prereqs-pred valid-pred schema]
  {:name                schemas/NSQKeyword
   :aspect-prereqs-pred schemas/Fn
   :bar-prereqs-pred    schemas/Fn
   :valid-pred          schemas/Fn
   :schema              schemas/Schema}
  {:aspect-prereqs-pred (fn [_] true)
   :bar-prereqs-pred    (fn [_] true)
   :valid-pred          (fn [_] true)
   :schema              s/Any})


;;;; Helper function for working with Aspect and Bar type definitions

(defn map-from-defs
  "Given a sequence of Aspect or Bar type definitions, returns a map mapping
  names of Aspects or Bar types to definitions."
  [ds]
  (plumbing/map-from-vals #(safe-get % :name) ds))


;;;; Functions and procedures for working with Bar types

(defn blank-bar-type-def
  "Defines a Bar type with name BAR-TAG that imposes no restrictions at all.

  Can be used to attach Bars whose actual definition is not available. Using is
  a bit smelly and can only be deodorized by checking the Thing with the proper
  Bar type definition later."
  [bar-tag]
  (map->bar-type {:name bar-tag}))

(defn assert-bar-valid
  "Throws an exception if BAR doesn't comply to BAR-TYPE."
  [bar-type bar]
  (assert (bar-type?+ bar-type) "improper Bar type definition")
  (s/validate (:schema bar-type) bar)
  (assert! ((:valid-pred bar-type) bar)
           "invalid Bar %s according to Bar type %s"
           bar bar-type))
