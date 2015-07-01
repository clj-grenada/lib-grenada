(ns grenada.things
  ;; TODO: Don't require people reading the source for finding out everything
  ;;       that's important. I hate it when I have to look into the source code
  ;;       in order to find out how to use a library. (RM 2015-07-05)
  "In this namespace the Things are defined by compiling a datastructure into a
  bunch of defns and defs. For lack of better documentation, here an overview
  over what you can expect and afterwards short explanations of the individual
  items.


  Overview
  --------

  Be t a Thing type, ´t´ its tag (a keyword) and <t> (name ´t´). The macro will
  define:

   - -><t>     (the Thing type's ->constructor)
   - map-><t>  (the Thing type's map->constructor)
   - <t>?      (the Thing type's predicate)
   - <t>-?     (the Thing type's -predicate, read: »minus predicate«)
   - <t>-info

  Be c a category, ´c´ its tag (a keyword and <c> (name ´c´). The macro will
  define:

   - <c>?
   - things-in-<c>

  Be <co> a coordinate. The macro will define:

   - <co>-coord

  The macro will also define:

   - thing-tags
   - thing-tag?
   - thing-?
   - thing-info
   - things-in-category

  Furthermore we will define in this namespace:

   - above-incl?
   - below-incl?
   - vec->thing
   - vec->thing-


  Explanations
  ------------

  (-><t> & args)

   - Constructs Thing t from positional arguments as defined by guten-tag.

  (map-><t> amap)

   - Constructs Thing t taking arguments from amap.

  (<t>? o)

   - Tests if o is of Thing type t and valid according to the contract defined
     in the (gt/deftag …) as defined by guten-tag.

  (<t>-? o)

   - Tests if the first element of o is tag ´t´. No validation is performed.
     Also works on plain [<tag> <val>] vectors among others.

  <t>-info

   - Contains a map holding information about Thing type t. The structure is
     defined in the ThingInfo Schema. :ncoords is the number of coordinates t
     has. :categories contains keywords of t's categories. The other values are
     references to the functions and objects described above.

  (<c>? o)

   - Determines if o belongs to the category c, based on (first o).

  things-in-<c>

   - A set of the tags of the Things belonging to category c.

  <co>-coord

   - Given a Thing, returns it's <co> coordinate.
   - Example: (version-coord namespace-thing) gives namespace-thing's version
     coordinate.

  thing-tags

   - A set of the tags of all Thing types.

  (thing-tag? o)

   - Determines if o is a Thing tag.

  (thing-? o)

   - Determines if o is a Thing, based on (first o).

  thing-info

   - Contains a map from all Thing tags <t> to their respective <t>-info maps.

  things-in-category

   - Contains a map from all categories c to their respective <c> sets.

  (above-incl? <t1> t2)

   - Returns true if Thing t2 is on the same level or above Thing type t1.
   - Examples: (above-incl? :grenada.things/namespace namespace-thing) ;=> true
               (above-incl? :grenada.things/namespace def-thing) ;=> falsey
               (above-incl? :grenada.things/def namespace-thing) ;=> true

  (below-incl? <t1> t2)

   - Analogous to above-incl?.

  vec->thing

   - Constructs a Thing t from a tag/map pair.

  vec->thing-

   - Constructs an ATaggedVal from a tag/map pair. Like vec->thing, but without
     validation.


  Note
  ----

  If you want to use stuff from this namespace, I recommend to require it in a
  way equivalent to (require '[grenada.things :as t]). Any other shorthand is
  okay, but you shouldn't (use …) or :refer anything unless you know very well
  what you're doing."
  (:refer-clojure :exclude [fn? record?])
  (:require [clojure.core :as clj]
            [schema.core :as s]
            [plumbing.core :as plumbing :refer [safe-get safe-get-in]]
            [guten-tag.core :as gt]))

;;; !!! WARNING !!!
;;;
;;; In order to make the API convenient to use, we're overwriting some
;;; clojure.core functions. See the excludes in :refer-clojure for which these
;;; are. If you want to use these functions, you have to qualify them, for
;;; example, fn? becomes clj/fn?. Make extra sure you're not accidentally using
;;; the functions defined here! Otherwise you're in for quite subtle bugs.
;;;
;;; You don't need to worry about other people's macros that also expand to (fn?
;;; …) forms and other things from clojure.core. Thanks to Clojure's syntax
;;; quote, they should be qualified in the right way. For example, a `(fn? …)
;;; from a macro becomes (clojure.core/fn? …). But if you're about to mess with
;;; the code around here, you probably already know that.


;;;; Universal helpers

(defn- sym [& args]
  (symbol (apply str args)))

(defn- name-sym [kw-or-sym]
  (-> kw-or-sym name symbol))

(defn- safe-get-all-v [m ks]
  (mapv #(safe-get m %) ks))

(defn- ns-qualified? [kw-or-sym]
  (namespace kw-or-sym))


;;;; Universally useful Schema helpers and Schemas

(defn- adheres? [schema v]
  (nil? (s/check schema v)))

(defn- seq-of [schema n nm]
  (vec (repeat n (s/one schema nm))))

(def Fn (s/pred clj/fn? "a fn"))

(def NSQKeyword (s/both s/Keyword
                        (s/pred ns-qualified? "has to have namespace")))

(def List (s/pred list? "a list"))

;; REVIEW: Make sure this actually works. (RM 2015-07-05)
(def PrePred
  "Schema for predicates as they may appear in a function's :pre list."
  [(s/both List
           [(s/one s/Symbol "function name") s/Any])])


;;;; Custom Schemas and Schema helpers for Things

(defn- coords-of-len [n]
  (seq-of s/Str n "coord"))

(defn- not-extension-key? [x]
  (not= x :grenada.cmeta/extensions))

(def ThingTag NSQKeyword)

(def Extensions {(s/both s/Keyword
                         (s/pred ns-qualified? "namespace-qualified"))
                 s/Any})

(def Cmeta {(s/pred not-extension-key? "not-extension-key") s/Any})


;;;; Schemas for the input to defthings

;;; Requiring every keyword that might be accessed from the outside
;;; namespace-qualified, so that it's more speaking. Could be made
;;; namespace-qualified by the macro, of course, but then people might think
;;; they don't have to namespace-qualify them later.

(def Properties {(s/optional-key :add-to-cats) #{NSQKeyword}
                 (s/optional-key :add-to-pre) #{PrePred}})

(def ThingVec [(s/one ThingTag "tag") (s/one Properties "properties")])

(def ThingGroupVec [(s/one NSQKeyword "group-key")
                    (s/one #{(s/either ThingTag ThingVec)} "thing definition")
                    (s/optional Properties "properties")])

(def ThingsDefinition
  [(s/either ThingTag ThingVec ThingGroupVec)])


;;;; Schemas for data structures defthings defines

(def ThingInfo {:ncoords s/Int
                :categories #{NSQKeyword}
                :->constructor Fn
                :map->constructor Fn
                :predicate Fn
                :-predicate Fn})

(def ThingsDescription {ThingTag ThingInfo})


;;;; Normalizing the ThingDefinitions

(def NormalizedThingDef {:thing-tag NSQKeyword
                         :ncoords s/Int
                         :categories #{NSQKeyword}
                         :add-to-pre #{PrePred}})

;;; Note: This stuff would be really nice to test with Schema-based generative
;;;       testing.
(defn- normalize-thing-def [[i td] extra-preds]
  (cond
    (keyword? td)
    (normalize-thing-def [i [td {}]] extra-preds)

    (and (sequential? td) (map? (second td)))
    (let [[tag preds] td]
      {:thing-tag tag
       :ncoords (inc i)
       :categories (into (get preds :add-to-cats #{})
                         (get extra-preds :add-to-cats))
       :add-to-pre (into (get preds :add-to-pre #{})
                         (get extra-preds :add-to-pre))})

    (and (sequential? td) (set? (second td)))
    (let [group-tag (first td)
          preds-for-all (nth td 2 {})
          preds-with-group (update preds-for-all
                                   :add-to-cats
                                   #(conj % group-tag))]
      (map (clj/fn mapfun [gtd]
             (normalize-thing-def [i gtd] preds-with-group)) (second td)))

    :else
    (throw (AssertionError.
             "This shouldn't happen. The input has been validated."))))

;; Note: Mind the difference between flatten and mapcat. mapcat would also seq
;;       the maps and concatenate the results.
(s/defn ^:private ^:always-validate normalize-things-def :- [NormalizedThingDef]
  [tsd :- ThingsDefinition]
  (flatten
    (map #(normalize-thing-def % {}) (plumbing/indexed tsd))))


;;;; Miscellaneous helpers for defthings

(defn- calc-things-in-category [tds]
  (->> tds
       (map (fn [{:keys [thing-tag categories]}]
              (plumbing/for-map [c categories] c #{thing-tag})))
       (apply merge-with into)))

(defn- heading [s]
  `(comment ";;;;" ~s ~(apply str (repeat 30 ";"))))


;;;; Generating names for various defs

;;; These are for the cases where a name has to be generated multiple times in
;;; order to avoid bugs where you change one occurence of the name-generation
;;; and forget the other(s). In cases where a name is generated only in one
;;; place, I don't use a separate function.

(defn- ->t-nm [thing-tag]
  (sym "->" (name thing-tag)))

(defn- map->t-nm [thing-tag]
  (sym "map->" (name thing-tag)))

(defn- t-?-nm [thing-tag]
  (sym (name thing-tag) "-?"))

(defn- t-info-nm [thing-tag]
  (sym (name thing-tag) "-info"))


;;;; Various functions for generating def(n) forms – extracted for clarity

(s/defn ^:private deftag-form
  [{:keys [thing-tag ncoords categories add-to-pre]} :- NormalizedThingDef]
  {:pre [thing-tag ncoords categories add-to-pre]}
  (let [has-cmeta? (contains? categories ::has-cmeta)
        fields-v (plumbing/conj-when '[name coords extensions]
                                     (if has-cmeta? 'cmeta))

        common-pre-v
        (plumbing/conj-when
          `[(string? ~'name)
            (adheres? (coords-of-len ~ncoords) ~'coords)
            (adheres? Extensions ~'extensions)]
          (if has-cmeta? `(adheres? Cmeta ~'cmeta)))

        indiv-pre-v (into common-pre-v add-to-pre)]
    `(gt/deftag ~(name-sym thing-tag) ~fields-v {:pre ~indiv-pre-v})))

(defn- map->t-form [{:keys [thing-tag categories]}]
  (let [common-fields [:name :coords :extensions]
        fields (if (contains? categories ::has-cmeta)
                 (conj common-fields :cmeta)
                 common-fields)]
    `(defn ~(map->t-nm thing-tag) [~'thing-map]
       (apply ~(->t-nm thing-tag)
              (safe-get-all-v ~'thing-map ~fields)))))

(defn- t-?-form [{:keys [thing-tag]}]
  `(defn ~(t-?-nm thing-tag) [~'o]
     (and (sequential? ~'o) (= ~thing-tag (first ~'o)))))

(defn- t-info-form [{:keys [thing-tag ncoords categories]}]
  `(def ~(t-info-nm thing-tag)
     {:ncoords ~ncoords
      :categories ~categories
      :->constructor ~(->t-nm thing-tag)
      :map->constructor ~(map->t-nm thing-tag)
      :predicate ~(sym (name thing-tag) "?")
      :-predicate ~(t-?-nm thing-tag)}))

(defn- c?-form [[c tag-set]]
  `(defn ~(sym (name c) "?") [~'o]
     (and (sequential? ~'o) (contains? ~tag-set (first ~'o)))))

(defn- things-in-c-form [[cat tag-set]]
  `(def ~(sym "things-in-" (name cat)) ~tag-set))

(declare thing?)
(declare below-incl?)

(defn- coord-form [[i td]]
  (let [coord (if (vector? td)
                (first td)
                td)]
    `(defn ~(sym (name coord) "-coord") [~'thing]
       {:pre [(thing? ~'thing) (below-incl? ~coord ~'thing)]}
       (get (:coords ~'thing) ~i))))

(defn- thing-info-form [tds]
  (let [thing-info-map (plumbing/for-map [td tds
                                          :let [thing-tag (:thing-tag td)]]
                         thing-tag (t-info-nm thing-tag))]
    `(def ~'thing-info ~thing-info-map)))

;;;; The compiler and the definition of the Things

;; The Things don't have docstrings and defthings doesn't generate them right
;; now. On the one hand I don't want repetitive docstrings as can be found in
;; lib-grimoire, because, in my opinion, they bloat the code and make it
;; tedious to change. On the other hand, this project is about documentation
;; and metadata, and leaving out docstrings makes me a bit of a bad example.
;; But then again, the format of Things is documented in the Grenada spec.
;;
;; I refrained from using namespaced tags for thing groups, because I think that
;; feature of guten-tag's causes more confusion than it is useful. You still can
;; use def? for finding out if a Thing belongs to the group/level def.
;;
;; TODO: Think about attaching documentation for Things somewhere. (RM
;;       2015-06-27)
(defmacro defthings
  "The data structure passed to defthings defines the Things. It will be
  compiled into a description of the Things it defines and various other
  definitions. The description will be a map. You need to read the schemas in
  this namespace in order to understand what I'm writing.

  I think the definition is fairly self-descriptive, however, here a
  specification.

   - Every ThingTag and ThingVec defines a Thing.

   - For every Thing in the toplevel vector of definitions, :ncoords will be
     its offset in that vector plus one.

   - For every Thing inside a ThingGroupVec, :ncoords will be the offset of the
     ThingGroupVec plus one.

   - The :categories of a Thing are the entries from its :add-to-cats property
     and, if it is grouped, the group and the entries from the group's
     :add-to-cats property.

   - If a Thing has an :add-to-pre property, the forms from that list will be
     added to its pre-list in the (gt/deftag …). If the Thing is grouped and
     the group has an :add-to-pre property, those forms will also be added.

   - For every entry in the toplevel vector of definitions, be <t> the (name …)
     of its ThingTag or group-key and i its offset in the vector. We define an
     accessor with name <t>-coord for the i-th coordinate of a Thing. This
     means that, for example, (version-coord a-namespace-thing) will give you
     the version coordinate (2nd coordinate, counting from 0) of
     a-namespace-thing."
  {:grenada.cmeta/extensions
   {:voyt.ext/requires ['guten-tag.core 'grenada.things 'plumbing.core
                        'schema.core]}}
  [things-def]
  (let [things-def (s/validate ThingsDefinition things-def)
        normalized-things-def (normalize-things-def things-def)

        forms-per-thing
        (plumbing/aconcat
          (map (juxt deftag-form
                     map->t-form
                     t-?-form
                     t-info-form)
               normalized-things-def))

        things-in-category (calc-things-in-category normalized-things-def)

        forms-per-category
        (plumbing/aconcat
          (map (juxt c?-form things-in-c-form) things-in-category))

        coord-forms
        (map coord-form (plumbing/indexed things-def))]
    `(do
       ~(heading "Forms per thing")
       ~@forms-per-thing
       ~(heading "Forms per category")
       ~@forms-per-category
       ~(heading "coord forms")
       ~@coord-forms
       ~(heading "General information about Things")
       (def ~'thing-tags ~(set (map :thing-tag normalized-things-def)))
       (defn ~'thing-tag? [~'o] (contains? thing-tags ~'o))
       ~(thing-info-form normalized-things-def)
       ~(heading "Something about categories and Things")
       (def ~'things-in-category ~things-in-category))))


(defthings
  [::group
   ::artifact
   ::version
   ::platform
   [::namespace {:add-to-cats #{::has-cmeta}}]
   [::def #{[::fn        {:add-to-cats #{::has-cmeta ::var-backed}}]
            [::plain-def {:add-to-cats #{::has-cmeta ::var-backed}}]
            [::macro     {:add-to-cats #{::has-cmeta ::var-backed}}]
            [::protocol  {:add-to-cats #{::has-cmeta ::var-backed ::class-backed}}]
            ::defmethod
            [::record    {:add-to-cats #{::class-backed}}]
            [::deftype   {:add-to-cats #{::class-backed}}]
            ::special}]])


;;;; A few extra predicates

(defn thing-? [o]
  (and (sequential? o) (thing-tag? (first o))))

(defn thing? [o]
  (and (gt/tagged? o)
       (let [tag (gt/tag o)
             thing-pred (get-in thing-info [tag :predicate] (constantly false))]
         (thing-pred o))))

(defn below-incl? [thing-tag thing]
  {:pre [(thing-tag? thing-tag) (thing? thing)]}
  (>= (safe-get-in thing-info [(gt/tag thing) :ncoords])
      (safe-get-in thing-info [thing-tag :ncoords])))

(defn above-incl? [thing-tag thing]
  {:pre [(thing-tag? thing-tag) (thing? thing)]}
  (<= (safe-get-in thing-info [(gt/tag thing) :ncoords])
      (safe-get-in thing-info [thing-tag :ncoords])))


;;;; Convenience functions for constructing Things

(defn vec->thing- [[tag theval :as thevec]]
  {:pre [(thing-? thevec) (vector? thevec)]}
  (gt/->ATaggedVal tag theval))

(defn vec->thing [[tag theval :as thevec]]
  (let [unverified-thing (vec->thing- thevec)]
    (assert ((safe-get-in thing-info [tag :predicate]) unverified-thing))
    unverified-thing))


;; Leaving this for easier debugging. Make sure you always copy in the most
;; current ThingsDefinition.
(comment

  (in-ns 'grenada.things)
  (require '[clojure.pprint :refer [pprint]]
           '[clojure.tools.namespace.repl :refer [refresh]]
           '[clojure.repl :refer [doc]])

  (pprint
    (macroexpand-1
      '(defthings
         [::group
          ::artifact
          ::version
          ::platform
          [::namespace {:add-to-cats #{::has-cmeta}}]
          [::def #{[::fn        {:add-to-cats #{::has-cmeta ::var-backed}}]
                   [::plain-def {:add-to-cats #{::has-cmeta ::var-backed}}]
                   [::macro     {:add-to-cats #{::has-cmeta ::var-backed}}]
                   [::protocol  {:add-to-cats #{::has-cmeta ::var-backed ::class-backed}}]
                   ::defmethod
                   [::record    {:add-to-cats #{::class-backed}}]
                   [::deftype   {:add-to-cats #{::class-backed}}]
                   ::special}]]))))
