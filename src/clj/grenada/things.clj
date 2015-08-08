(ns grenada.things
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
                 [coords aspects bars]
                 {:coords schemas/Vector
                  :aspects #{schemas/NSQKeyword}
                  :bars {schemas/NSQKeyword s/Any}}
                 {:aspects #{}
                  :bars {}})

(defn vec->thing [[tag m]]
  (let [thing (gt/->ATaggedVal tag m)]
    (assert (thing?+ thing))
    thing))


;;;; Definitions of the main aspects

(def main-aspect-defaults
  {:prereqs-pred empty?
   :name-pred string?})

(def main-aspect-specials
  {::platform
   {:name-pred (fn valid-platform-name? [n]
                 (contains? #{"clj" "cljs" "cljclr" "ox" "pixi" "toc"} n))}

   ::find
   {:name-pred (fn valid-find-name? [_] true)}})

(def main-aspect-names [::group
                        ::artifact
                        ::version
                        ::platform
                        ::namespace
                        ::find])

(def def-for-aspect
  (plumbing/for-map [[i nm] (plumbing/indexed main-aspect-names)]
    nm
    (things.def/map->main-aspect
      (merge main-aspect-defaults
             {:name nm
              :ncoords (inc i)}
             (get main-aspect-specials nm)))))


;;;; Working with main aspects

(defn pick-main-aspect [aspects]
  (let [main-aspects (set/intersection aspects (set main-aspect-names))]
    (assert! (= 1 (count main-aspects))
             "More then one main Aspect among: %s." aspects)
    (first main-aspects)))

;; Note: Don't repeat such a tangle if more functions are required that work on
;;       both Things and Aspect keywords like above-incl? and below-incl?. In
;;       that case, start passing Things to :aspect-prereqs-pred, so that it can
;;       use the whole power of functions on Things.
(defmulti ^:private resolve-main-aspect
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

(defn above-incl? [thing-tag thing-or-aspect]
   (<= (safe-get-in def-for-aspect
                    [(resolve-main-aspect thing-or-aspect) :ncoords])
      (safe-get-in def-for-aspect [thing-tag :ncoords])))

(defn below-incl? [thing-tag thing-or-aspect]
  (>= (safe-get-in def-for-aspect
                   [(resolve-main-aspect thing-or-aspect) :ncoords])
      (safe-get-in def-for-aspect [thing-tag :ncoords])))


;;;; Functions for doing stuff with Things and Aspects

;; MAYBE TODO: (Here and in the following.) Improve the diagnostic messages.
;;             Include references to places in the spec. (RM 2015-07-24)
;; TODO: Use plumbing.fnk.schema/assert-iae for the asserts. (RM 2015-07-24)
(defn assert-aspect-attachable [aspect-def thing]
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
(defn attach-aspect [aspect-defs aspect-tag thing]
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
;;             the Aspect's are fulfilled. (RM 2015-07-26)
(defn has-aspect? [aspect-tag thing]
  {:pre [(thing?+ thing)]}
  (contains? (:aspects thing) aspect-tag))


;;;; Functions for doing stuff with Things and Bars

(defn assert-bar-attachable [bar-type-def thing]
  (assert (things.def/bar-type?+ bar-type-def) "not a proper Bar definition")
  (assert ((:aspect-prereqs-pred bar-type-def)
           (:aspects thing))
          "unfulfilled Aspect prerequisites according to Bar type")
  (assert ((:bar-prereqs-pred bar-type-def)
           (:bars thing))
          "unfulfilled Bar prerequisites according to Bar type")
  (assert (not (contains? (:bars thing) (:name bar-type-def)))
          (str "Bar of type " (:name bar-type-def) " already present")))

(defn attach-bar [bar-type-defs bar-tag bar thing]
  {:pre [(thing?+ thing)]}
  (let [bar-type-def (safe-get bar-type-defs bar-tag)]
    (assert-bar-attachable bar-type-def thing)
    (things.def/assert-bar-valid bar-type-def bar)
    (assoc-in thing [:bars bar-tag] bar)))
