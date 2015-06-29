(ns grenada.things
  "

  If you want to use stuff from this namespace, I recommend to require it in a
  way equivalent to (require '[grenada.things :as t]). Any other shorthand is
  okay, but you shouldn't (use …) or :refer anything unless you know very well
  what you're doing."
  (:refer-clojure :exclude [namespace fn fn? var? defmethod record? deftype])
  (:require [clojure.core :as clj]
            [schema.core :as s]
            [plumbing.core :as plumbing :refer [safe-get]]
            [guten-tag.core :as t]))

;;; Comments on the implementation:
;;;
;;;  - Some vars that are defined here have the same names as vars in
;;;    clojure.core. This is necessary in order to avoid a clumsy API. For
;;;    example, I don't want to make people use grenada.things/namespace-thing?
;;;    just in order to avoid the clash with clojure.core/namespace ((deftag
;;;    namespace …) creates a var named "namespace."
;;;
;;;  - As long as a consumer of this API uses its vars and fns only with
;;;    namespace-qualification, they don't have to worry about anything. Only
;;;    when you work inside this namespace, you have to pay attention about what
;;;    refers to what.
;;;
;;;  - Look at the excludes in :refer-clojure. You won't be able to use those
;;;    unqualified. Instead, you have to use the namespace-qualified versions,
;;;    i.e. clj/namespace clj/fn etc.
;;;
;;;  - Of course, macros also expand to (def …) forms and other things from
;;;    clojure.core. But thanks to Clojure's syntax quote, they should be
;;;    qualified in the right way. For example, a `(def …) from a macro becomes
;;;    '(clojure.core/def …)

;;; MAYBE TODO: Add some hierarchy predicates or whatever. Currently the
;;;             hierarchy is defined by the number of coordinates a Thing has,
;;;             which is kind of okay. (RM 2015-06-27)

;;;; Universal helpers

(defn- kw->sym [kw]
  (symbol (name kw)))

(defn- keyset [m]
  (set (keys m)))

(defn- safe-get-all-v [m ks]
  (mapv #(safe-get m %) ks))


;;;; Universal Schema helpers

(defn- adheres? [schema v]
  (try
    (s/validate schema v)
    true
    (catch RuntimeException _ false)))

(defn- seq-of [schema n nm]
  (vec (repeat n (s/one schema nm))))


;;;; Custom Schemas and Schema helpers for Things

(defn- coords-of-len [n]
  (seq-of s/Str n "coord"))

(defn- ns-qualified? [kw-or-sym]
  (clj/namespace kw-or-sym))

(defn- not-extension-key? [x]
  (not= x :grenada.cmeta/extensions))

(def Extensions {(s/both s/Keyword
                         (s/pred ns-qualified? "namespace-qualified"))
                 s/Any})

(def Cmeta {(s/pred not-extension-key? "not-extension-key") s/Any})


;;;; A macro for the convenient definition of Things

(defmacro ^:private defthing
  {:grenada.cmeta/extensions
   {:voyt.ext/requires
    ['plumbing.core 'grimoire.things 'grenada.things]

    :voyt.ext/defines
    (get-in (meta #'t/deftag) [:grenada.cmeta/extensions :voyt.ext/defines])}}

  [thing-sym & {:keys [ncoords has-cmeta?] :as kwargs}]

  {:pre [(= (keyset kwargs) #{:ncoords :has-cmeta?})]}

  (let [fields-v (plumbing/conj-when
                   '[name coords extensions]
                   (if has-cmeta? 'cmeta))
        pre-v (plumbing/conj-when
                `[(string? ~'name)
                  (adheres? (coords-of-len ~ncoords) ~'coords)
                  (adheres? Extensions ~'extensions)]
                (if has-cmeta? `(adheres? Cmeta ~'cmeta)))]
    `(t/deftag ~thing-sym ~fields-v
               {:pre ~pre-v})))


;;;; Definitions of Things (just few enough not to warrant a macro)

;;; The Things don't have docstrings and defthing doesn't support them right
;;; now. On the one hand I don't want repetitive docstrings as can be found in
;;; lib-grimoire, because, in my opinion, they bloat the code and make it
;;; tedious to change. On the other hand, this project is about documentation
;;; and metadata, and leaving out docstrings makes me a bit of a bad example.
;;; But then again, the format of Things is documented in the Grenada spec.
;;;
;;; TODO: Think about attaching documentation for things somewhere. (RM
;;;       2015-06-27)

(defthing group     :ncoords 1 :has-cmeta? false)
(defthing artifact  :ncoords 2 :has-cmeta? false)
(defthing version   :ncoords 3 :has-cmeta? false)
(defthing platform  :ncoords 4 :has-cmeta? false)

(defthing namespace :ncoords 5 :has-cmeta? true)

;;; The following are all on the same level. I refrain from pseudo-namespacing
;;; them into :def/fn, :def/var etc., because I think that feature causes more
;;; confusion than it is useful. I group them separately later with the
;;; definition of def?.
;;;
;;; !!! IMPORTANT !!!
;;;
;;; Whenever you add or change something here, you also have to adjust the
;;; predicates below. Sorry for not yet making this beautiful.

(defthing fn        :ncoords 6 :has-cmeta? true)
(defthing plain-def :ncoords 6 :has-cmeta? true)
(defthing macro     :ncoords 6 :has-cmeta? true)
(defthing protocol  :ncoords 6 :has-cmeta? true)

(defthing defmethod :ncoords 6 :has-cmeta? false)
(defthing record    :ncoords 6 :has-cmeta? false)
(defthing deftype   :ncoords 6 :has-cmeta? false)
(defthing special   :ncoords 6 :has-cmeta? false)


;;;; Some predicates for different groups of Things

(def def-with-cmeta?
  "

  These are ordered by frequency in code."
  (some-fn fn? var? macro? protocol?))

(def def-without-cmeta?
  "

  These, too, are ordered by frequency in code."
  (some-fn defmethod? record? deftype? special?))

(def def?
  (some-fn def-with-cmeta? def-without-cmeta?))

(def has-cmeta?
  (some-fn namespace? def-with-cmeta?))


;;;; Convenience function for constructing Things

(defn map->thing
  "

  Expects THING-MAP to contain a :cmeta entry if and only if the CONSTRUCTOR
  constructs a Thing that has a :cmeta field."
  [constructor thing-map]
  (let [args (plumbing/conj-when
               (safe-get-all-v thing-map [:name :coords :extensions])
               (safe-get thing-map :cmeta))]
    (apply constructor args)))
