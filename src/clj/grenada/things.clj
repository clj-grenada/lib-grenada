(ns grenada.things
  "Programmatic definitions of type Thing and the Main Aspects of Things as well
  as a bunch of auxiliary functions and procedures."
  ;; TODO: Don't require people to read the source for finding out everything
  ;;       that's important. I hate it when I have to look into the source code
  ;;       in order to find out how to use a library. (RM 2015-07-05)
  (:require [clojure
             [core :as clj]
             [set :as set]]
            [schema
             [core :as s]
             [macros :refer [assert!]]]
            [grenada
             [schemas :as schemas]
             [utils :as gr-utils]]
            [plumbing.core :as plumbing :refer [safe-get safe-get-in]]
            [guten-tag.core :as gt]
            [grenada.guten-tag.more :as gt-more]
            [grenada.things.def :as things.def]))

;;;; New definition of a Thing

(gt-more/deftag+ thing
  "Defines a Thing, i.e. an object with coordinates, Aspects and Bars."
  [coords aspects bars]
  {:coords schemas/Vector
   :aspects #{schemas/NSQKeyword}
   :bars {schemas/NSQKeyword s/Any}}
  {:aspects #{}
   :bars {}})

(defn vec->thing
  "Converts a [tag map] vector to a Thing, throwing an exception if the result
  is not a valid Thing according to thing?+."
  [[tag m]]
  (let [thing (gt/->ATaggedVal tag m)]
    (assert (thing?+ thing))
    thing))


;;;; Definitions of the main aspects

(def main-aspect-defaults
  "Default values for the programmatic parts of Main Aspect definitions."
  {:prereqs-pred empty?
   :name-pred string?})

(def main-aspect-specials
  "Map from Main Aspect name to a map containing the non-common entries for its
  definition map."
  {::platform
   {:name-pred (fn valid-platform-name? [n]
                 (contains? #{"clj" "cljs" "cljclr" "ox" "pixi" "toc"} n))}

   ::find
   {:name-pred (fn valid-find-name? [_] true)}})

(def main-aspect-names
  "Names of the main aspects, ordered from highest to lowest level."
  [::group ::artifact ::version ::platform ::namespace ::find])

(def def-for-aspect
  "Map mapping Main Aspect name to the programmatic part of
  programmatic definition.

  For the non-programmatic part, see the spec."
  (plumbing/for-map [[i nm] (plumbing/indexed main-aspect-names)]
    nm
    (things.def/map->main-aspect
      (merge main-aspect-defaults
             {:name nm
              :ncoords (inc i)}
             (get main-aspect-specials nm)))))


;;;; Working with main aspects

(defn pick-main-aspect
  "Given a collection of Aspect names, returns the one that is the name of a
  Main Aspect.

  Throws and exception if there is more than one Main Aspect name in the
  collection."
  [aspects]
  (let [main-aspects (set/intersection aspects (set main-aspect-names))]
    (assert! (= 1 (count main-aspects))
             "More then one main Aspect among: %s." aspects)
    (first main-aspects)))

;; Note: Don't repeat such a tangle if more functions are required that work on
;;       both Things and Aspect keywords like above-incl? and below-incl?. In
;;       that case, start passing Things to :aspect-prereqs-pred, so that it can
;;       use the whole power of functions on Things.
(defmulti ^:private resolve-main-aspect
  "Given something, returns the Main Aspect that can be associated with it."
  (fn resolve-main-aspect-dispatch [x]
    (cond
      (thing? x)   :thing
      (keyword? x) :keyword
      :else        :default)))

(defmethod resolve-main-aspect :thing [thing]
  (assert! (thing?+ thing) "Not a valid Thing: %s." thing)
  (pick-main-aspect (safe-get thing :aspects)))

(defmethod resolve-main-aspect :keyword [aspect-kw]
  (assert! (contains? (set main-aspect-names) aspect-kw)
           "Not a main Aspect: %s." aspect-kw)
  aspect-kw)

(defmethod resolve-main-aspect :default [whatever]
  (throw (IllegalArgumentException.
           (str "Can't resolve " whatever " to an Aspect."))))


(defn above-incl?
  "Returns true if THING-OR-ASPECT is on the same level as or a higher level
  than the Main Aspect with ASPECT-NAME. Otherwise returns false.

  THING-OR-ASPECT must be something that can be resolved to a Main Aspect name
  by resolve-main-aspect, currently a Thing or an Aspect name."
  [aspect-name thing-or-aspect]
   (<= (safe-get-in def-for-aspect
                    [(resolve-main-aspect thing-or-aspect) :ncoords])
      (safe-get-in def-for-aspect [aspect-name :ncoords])))

(defn below-incl?
  "Same principle as above-incl?, but returns true if THING-OR-ASPECT is on the
  same level as or a _lower_ level than the Main Aspect with ASPECT-NAME,
  otherwise false."
  [aspect-name thing-or-aspect]
  (>= (safe-get-in def-for-aspect
                   [(resolve-main-aspect thing-or-aspect) :ncoords])
      (safe-get-in def-for-aspect [aspect-name :ncoords])))


;;;; Functions for doing stuff with Things and Aspects

;; MAYBE TODO: (Here and in the following.) Improve the diagnostic messages.
;;             Include references to places in the spec. (RM 2015-07-24)
;; TODO: Use schemas.macros/assert! for the asserts. (RM 2015-08-21)
(defn assert-aspect-attachable
  "Throws an exception if THING isn't fit for attaching the Aspect with
  ASPECT-DEF to it."
  [aspect-def thing]
  (assert ((some-fn things.def/aspect?+ things.def/main-aspect?+)
           aspect-def)
          "not a proper Aspect definition")
  (when (things.def/main-aspect? aspect-def)
    (assert (= (:ncoords aspect-def)
               (count (:coords thing)))
            "wrong number of coordinates as defined by Aspect"))
  (assert ((:prereqs-pred aspect-def)
           (:aspects thing))
          "unfulfilled prerequisites according to aspect")
  (assert ((:name-pred aspect-def)
           (last (:coords thing)))
          "invalid name according to aspect"))

;; TODO: When everyone is on Clojure ≧ 1.7.0, use update. (RM 2015-07-29)
(defn attach-aspect
  "Attaches Aspect with name ASPECT-TAG to THING.

  Looks up its definition in ASPECT-DEFs, which has to be map as returned by
  clj::grenada.things.def/map-from-defs."
  [aspect-defs aspect-tag thing]
  {:pre [(thing?+ thing)]}
  (let [aspect-def (safe-get aspect-defs aspect-tag)]
    (assert-aspect-attachable aspect-def thing)
    (update-in thing [:aspects]
               #(conj % (:name aspect-def)))))

(defn attach-aspects
  "Attaches all Aspects indicated by ASPECT-TAGS to THING using
  clj::grenada.things/attach-aspect.

  Aspects are attached in the order given by ASPECT-TAGS. You have to take care
  that they are in dependency order.

  ASPECT-DEFS is passed to `attach-aspect` unchanged."
  [aspect-defs aspect-tags thing]
  (reduce (fn attach-aspects-redfn [cur-thing aspect-tag]
            (attach-aspect aspect-defs aspect-tag cur-thing))
          thing
          aspect-tags))

;; MAYBE TODO: Add has-aspect?+, which also checks if the predicates defined by
;;             the Aspect's are fulfilled (or similar). (RM 2015-07-26)
(defn has-aspect?
  "Returns true if THING has an Aspect with the name ASPECT-TAG, false
  otherwise."
  [aspect-tag thing]
  {:pre [(thing?+ thing)]}
  (contains? (:aspects thing) aspect-tag))


;;;; Functions for doing stuff with Things and Bars

;p; TODO: Add better diagnostics to the other asserts. (RM 2015-08-08)
(defn assert-bar-attachable
  "Throws an exception if THING isn't fit for attaching a Bar of the type
  defined by BAR-TYPE-DEF to it."
  [bar-type-def thing]
  (assert (things.def/bar-type?+ bar-type-def) "not a proper Bar definition")
  (assert! ((:aspect-prereqs-pred bar-type-def)
            (:aspects thing))
           "unfulfilled Aspect prerequisites on %s according to Bar type def %s"
           (pr-str thing) (pr-str bar-type-def))
  (assert ((:bar-prereqs-pred bar-type-def)
           (:bars thing))
          "unfulfilled Bar prerequisites according to Bar type")
  (assert (not (has-bar? (:name bar-type-def) thing))
          (str "Bar of type " (:name bar-type-def) " already present")))

(defn attach-bar
  "Attaches the BAR of the type with name BAR-TAG to THING.

  Looks up the Bar type's definition in BAR-TYPE-DEFS, which has to be a map as
  returned by clj::grenada.things.def/map-from-defs. "
  [bar-type-defs bar-tag bar thing]
  {:pre [(thing?+ thing)]}
  (let [bar-type-def (safe-get bar-type-defs bar-tag)]
    (assert-bar-attachable bar-type-def thing)
    (things.def/assert-bar-valid bar-type-def bar)
    (assoc-in thing [:bars bar-tag] bar)))

(defn attach-bars
  "Attaches all Bars from TAGS-AND-BARS to THING using attach-bar.

  TAGS-AND-BARS is a map from Bar type names to Bars."
  [bar-type-defs tags-and-bars thing]
  (reduce (fn attach-bars-redfn [cur-thing [bar-tag bar]]
            (attach-bar bar-type-defs bar-tag bar cur-thing))
          thing
          tags-and-bars))

(defn has-bar?
  "Returns true if THING has a Bar of the Bar type with name BAR-TAG."
  [bar-tag thing]
  {:pre [(thing?+ thing)]}
  (contains? (:bars thing) bar-tag))

(defn detach-bar
  "Removes the Bar of type with name BAR-TAG from THING."
  [bar-tag thing]
  {:pre [(thing?+ thing) (has-bar? bar-tag thing)]}
  (gr-utils/dissoc-in* thing [:bars bar-tag]))

(defn replace-bar
  "Removes the Bar of type with name BAR-TAG from THING and attaches BAR of the
  same type.

  BAR-TYPE-DEFS are the same as for attach-bar."
  [bar-type-defs bar-tag bar thing]
  (->> thing
       (detach-bar bar-tag)
       (attach-bar bar-type-defs bar-tag bar)))
