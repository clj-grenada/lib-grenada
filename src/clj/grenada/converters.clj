(ns grenada.converters
  "Functions for converting one data structure of Grenada Things into another."
  (:require [plumbing.core :as plumbing :refer [safe-get]]))

;; MAYBE TODO: Add a variant that preserves order. (RM 2015-06-21)
(defn to-mapping
  "Given a sequence of Things, convert them to a map.

  They keys of the map will be the Things' coordinates. The values will be the
  Things themselves."
  [ms]
  (plumbing/map-from-vals #(safe-get % :coords) ms))

(defn to-seq
  "Converts a map of Things as produced by clj::grenada.converters/to-mapping
  back to a sequence of Things."
  [tsmap]
  (vals tsmap))
