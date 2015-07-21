(ns grenada.things
  ;; TODO: Don't require people to read the source for finding out everything
  ;;       that's important. I hate it when I have to look into the source code
  ;;       in order to find out how to use a library. (RM 2015-07-05)
  (:require [clojure.core :as clj]
            [schema.core :as s]
            [grenada.schemas :as schemas]
            [plumbing.core :as plumbing :refer [safe-get safe-get-in]]
            [guten-tag.core :as gt]
            [grenada.guten-tag.more :as gt.more]
            [grenada.things.def :as things.def]))

;;;; New definition of a Thing

(gt.more/deftag+ thing
                 [coords aspects bars]
                 {:coords schemas/Vector
                  :aspects #{schemas/NSQKeyword}
                  :bars {schemas/NSQKeyword s/Any}})


;;;; Definitions of the main aspects

(def main-aspect-defaults
  {:prereqs-pred empty?
   :name-pred string?}

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
    (things.def/make-main-aspect
      (merge main-aspect-defaults
             {:name nm
              :ncoords (inc i)}
             (get main-aspect-specials nm)))))


;;;; Functions for doing stuff with Things and Aspects

(defn assert-aspect-attachable [aspect-def thing]
  (assert ((some-fn things.def/aspect?+ things.def/main-aspect?+)
           aspect-def)
          "proper Aspect definition")
  (when (things.def/main-aspect? aspect-def)
    (assert (= (:ncoords aspect-def)
               (count (:coords thing)))
            "wrong number of coordinates as defined by Aspect"))
  (assert ((:prereqs-pred aspect-def)
           (:aspects thing))
          "unfulfilled prerequisites according to aspect")
  (assert ((:name-pred aspect-def)
           (last (:coords thing)))
          "wrong name according to aspect"))

(defn attach-aspect [aspect-defs aspect-tag thing]
  {:pre [(thing?+ thing)]}
  (let [aspect-def (safe-get aspect-defs aspect-tag)]
    (assert-aspect-attachable aspect-def thing)
    (update thing :aspects
            #(conj % (:name aspect-def)))))
