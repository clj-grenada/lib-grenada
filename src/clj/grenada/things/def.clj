(ns grenada.things.def
  (:require [plumbing.core :as plumbing :refer [safe-get]]
            [guten-tag.core :as gt]
            [grenada.guten-tag.more :as gt-more]
            [schema.core :as s]
            [grenada.schemas :as schemas]))

;;;; Some auxiliary definitions concerning Aspects

(def AspectSchema {:name s/Keyword
                   :prereqs-pred schemas/Fn
                   :name-pred schemas/Fn})

(def aspect-defaults {:prereqs-pred (fn [_] true)
                      :name-pred (fn [_] true)})


;;;; Tag type definitions for two kinds of Aspects

(gt-more/deftag+ main-aspect
                 [name ncoords prereqs-pred name-pred]
                 (assoc AspectSchema :ncoords s/Int)
                 aspect-defaults)

(gt-more/deftag+ aspect
                 [name prereqs-pred name-pred]
                 AspectSchema
                 aspect-defaults)


;;;; Definition of Bar type definition

;; TODO: Maybe improve the syntax of deftag+. This here looks a bit ugly. (RM
;;       2015-07-24)
(gt-more/deftag+ bar-type
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

(defn assert-bar-valid [bar-type bar]
  (assert (bar-type?+ bar-type) "improper Bar type definition")
  (s/validate (:schema bar-type) bar)
  (assert ((:valid-pred bar-type) bar) "invalid Bar according to Bar type"))

;;;; Helper function for working with Aspects

(defn map-from-defs [ds]
  (plumbing/map-from-vals #(safe-get % :name) ds))
