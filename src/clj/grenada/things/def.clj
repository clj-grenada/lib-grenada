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
                 (assoc AspectSchema :ncoords s/Int))

(defn make-main-aspect [m]
  (map->main-aspect (merge aspect-defaults m)))

(gt-more/deftag+ aspect
                 [name prereqs-pred name-pred]
                 AspectSchema)

(defn make-aspect [m]
  (map->aspect (merge aspect-defaults m)))


;;;; Helper function for working with Aspects

(defn map-from-defs [ds]
  (plumbing/map-from-vals #(safe-get % :name) ds))
