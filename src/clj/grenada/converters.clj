(ns grenada.converters
  (:require [plumbing.core :as plumbing :refer [safe-get]]))

;; MAYBE TODO: Add a variant that preserves order. (RM 2015-06-21)
(defn to-mapping [ms]
  (plumbing/map-from-vals #(safe-get % :coords) ms))
