(ns grenada.cleanroom
  "This namespace must not be loaded into the regular Clojure environment."
  (:require [clojure.edn :as edn])
  (:import java.io.Writer))

;;;; Stringification

;; If the :name and :ns cannot be found, this will give `#grenada/var "/"`. I
;; can live with that.
;;
;; This is a global mutation, so normally I would have to restore the old method
;; after I've used this. And indeed, I'd have to make redefinition, printing and
;; restoring atomic, if that's at all possible. However, I'm not executing any
;; of the library code I'm loading, so it should be okay. Man, Clojure sucks
;; more than I had expected.
(defmethod print-method clojure.lang.Var [v, ^Writer w]
  (let [var-name (get (meta v) :name "")
        namesp-name (str (get (meta v) :ns ""))]
   (.write w (str "#grenada/var \"" namesp-name "/" var-name "\""))))

;; TODO: Implement eliminating things that would be written with #< by doing
;;       something similar to what Letterpress does
;;       (https://groups.google.com/d/msg/clojure/Y-zccAoGCBw/Mcq2iTLjUJEJ)
;;       when running in pre 1.7.0-RC1 Clojure. (RM 2015-06-19)
(defn- pr-str-meta [m]
  (pr-str m))


;;;; Finding out what something is

(defn- ns-qualified? [nm]
  (namespace (symbol nm)))

;; MAYBE DE-HACK:
;;   It isn't specified anywhere that defining a protocol X defines a var X
;;   holding a map containing the key :on. However, it is specified (or
;;   documented) that defining a protocol X generates a class of the same name.
;;   So we might instead assemble the class name from the :name of the var and
;;   look whether it exists. (RM 2015-06-29)
(defn- protocol? [v]
  {:pre [(var? v)]}
  (let [x (var-get v)]
    (and (map? x) (class? (get x :on)))))

(defn- determine-var-def-kind [nm]
  {:pre [(ns-qualified? nm)]}
  (let [thevar (resolve (symbol nm))
        theval (var-get thevar)]
    (cond
      (get (meta thevar) :macro)
      :grenada.things/macro

      (fn? theval)
      :grenada.things/fn

      (protocol? thevar)
      :grenada.things/protocol

      :default
      :grenada.things/plain-def)))

(defn- specify-kind [[kind nm]]
  (if (= :var-def kind)
    (determine-var-def-kind nm)
    kind))


;;;; Finding out about things

(defn- get-cmeta [[kind nm]]
  {:pre [(or (= :grenada.things/namespace kind) (ns-qualified? nm))]}
  (let [sym (symbol nm)
        obj (if (= :grenada.things/namespace kind)
              (find-ns sym)
              (resolve sym))]
    (or (meta obj) {})))

(defmulti get-extension-metadata first)

;; TODO: Maybe get more metadata from the generated class?
(defmethod get-extension-metadata :grenada.things/protocol [[_ nm]]
  {:grenada.ext.default/protocol-map (var-get (resolve (symbol nm)))})

;; TODO: Implement getting extension metadata for other Things. (RM 2015-06-30)
(defmethod get-extension-metadata :default [[_ _]]
  {})


;;;; API for the mother runtime

(defn ns-interns-strs [nsstr]
  (->> nsstr
       symbol
       find-ns
       ns-interns
       keys
       (map #(symbol nsstr (str %)))
       (map str)))

(defn data-str-for
  "

  If the NM is the name of a :var-def, (symbol NM) has to return a fully
  qualified symbol, so that it can be resolve-d."
  [in-str things-with-cmeta-str]
  (let [[kind nm :as sth] (edn/read-string in-str)
        things-with-cmeta (edn/read-string things-with-cmeta-str)
        specific-kind (specify-kind sth)]
    (as-> {} res
      (assoc res :qualified-name nm)
      (assoc res :extensions (get-extension-metadata [specific-kind nm]))
      (if (contains? things-with-cmeta specific-kind)
        (assoc res :cmeta (get-cmeta [specific-kind nm])))
      [specific-kind res]
      (pr-str-meta res))))
