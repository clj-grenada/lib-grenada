(ns grenada-lib.cleanroom
  "This namespace must not be loaded into the regular Clojure environment."
  (:import java.io.Writer))

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
   (.write w (str "#grenada/var \"" "Dude!" namesp-name "/" var-name "\""))))

;; TODO: Implement eliminating things that would be written with #< by doing something
;; similar to what Letterpress does
;; (https://groups.google.com/d/msg/clojure/Y-zccAoGCBw/Mcq2iTLjUJEJ)
;; when running in pre 1.7.0-RC1 Clojure.
(defn pr-str-meta [m]
  (pr-str m))

(defn ns-interns-strs [ns-str]
  (->> ns-str
       symbol
       find-ns
       ns-interns
       keys
       (map str)))

(defn ns-meta [ns-str]
  (->> ns-str
       symbol
       find-ns
       meta
       pr-str-meta))

(defn var-meta [sym-str]
  (->> sym-str
       symbol
       resolve
       meta
       pr-str-meta))
