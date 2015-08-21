(ns grenada.utils
  "Miscellaneous utilities."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [plumbing.core :refer [safe-get]]))

;;;; Not categorized

(defn warn
  "Like clj::clojure.core/println, but outputs to *err*."
  [& args]
  (binding [*out* *err*] (apply println "WARNING! "args)))


;;;; Concerning Graph – plumbing.graph

(defmacro fnk*
  "Shortens fnks that just apply some other function to their arguments.

    (fnk [x] (inc x)) → (fnk* [x] inc)
    (fnk [x] (+ 4 x)) → (fnk* [x] (+ 4))"
  [symv form]
  `(plumbing.core/fnk [~@symv]
                      ~(if (list? form)
                         `(~@form ~@symv)
                         `(~form ~@symv))))


;;;; Concerning files – clojure.java.io

(defn str-file
  "Like io/file, but (str …)ingifies the resulting File."
  [& args]
  (str (apply io/file args)))

(defn ordinary-file-seq [fl]
  (filter #(.isFile %) (file-seq fl)))


;;;; Concerning strings – clojure.string

(defn clean-up-string
  "Removes trailing and leading whitespace and newlines from a string.

  Can be used to remove all the garbage from single-paragraph, multiline Clojure
  strings."
  [s]
  (->> s
       string/split-lines
       (map string/trim)
       (string/join " ")))


;;;; Concerning collections

(defn dissoc-in*
  "Like plumbing.core/dissoc-in, but doesn't remove empty maps."
  [m ks]
  (if m
    (if  (<= (count ks) 1)
      (apply dissoc m ks)
      (let [path (butlast ks)
            target-map (get-in m path)
            target-key (last ks)
            dissoced-map (dissoc target-map target-key)]
        (assoc-in m path dissoced-map)))))

(defn remove-nth
  "Returns COLL with the item with offset N removed.

  Example:
    user=> (remove-nth [0 1 2 3 4 5] 3)
    (0 1 2 4 5)"
  [n coll]
  (concat (take n coll) (drop (inc n) coll)))

(defn keyset
  "Set of the keys of M."
  [m]
  (set (keys m)))
