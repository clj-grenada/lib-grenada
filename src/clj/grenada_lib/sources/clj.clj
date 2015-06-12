(ns grenada-lib.sources.clj
  "Procedures for extracting metadata from .clj files."
  (:require [clojure.java.io :as io]
            [grenada-lib.util :refer [fnk* defconstrainedfn*]]
            [grenada-lib.sources.contracts :as grc]
            [medley.core :refer [dissoc-in]]
            [clojure.tools.namespace.find :as tns.find]
            [plumbing.core :as plumbing :refer [fnk]]
            [plumbing.graph :as graph]))

;;; Notes:
;;;
;;;  - If I write, "see the code", I deem the code so straighforward that it
;;;    wouldn't make sense to describe it's purpose in prose.
;;;
;;;  - If you replace a node (function) A in the graph with a node A' that
;;;    fulfils the replacement requirements of the A, you can be pretty sure
;;;    that the rest of the code will still work. Of course you can choose not
;;;    to fulfil the replacement requirements. But then you have to analyse
;;;    which nodes depended on A and change them accordingly.
;;;
;;;  - If I write, "Replacement: probably not necessary", I think that it
;;;    wouldn't make much sense to replace that particular node and thus don't
;;;    provide replacement requirements. If you find a case where you want to
;;;    replace the node after all, please contact me and I will provide
;;;    replacement requirements.

;; TODO: Limit to .clj files. .cljc files have to be treated separately.
(defconstrainedfn* clj-nssym-src [grc/takes-dir grc/nssym-src]
  "Returns a sequence of symbols naming namespaces defined in .clj files below
  DIR-PATH.

  Note: tools.namespace supports only one namespace per file. We inherit this
  limitation. This means, if you have a file that defines more than one
  namespace, find-clj-nssyms will only be able to extract the first. You can
  plug in your own namespace scanner, in order to overcome this.

  Class: source, non-standard

  Replacement requirements: see grc/nssym-src"
  [dir-path]
  (tns.find/find-namespaces-in-dir (io/as-file dir-path)))

(defconstrainedfn* nssym->nsmap [grc/takes-sym grc/returns-nsmap]
  "Returns a partial metadata map for the namespace denoted by S.

  See the code for what this means.

  Class: transformer, non-standard

  Replacement: probably not necessary"
  [s]
  {:name (name s)
   :level :grimoire.things/namespace
   :coords-suffix ["clj" (name s)]})

(defconstrainedfn* nssym->deftups [grc/takes-nssym grc/deftup-src]
  "Returns a tuple [NSSYM sym] for each def in the namespace denoted by NSSYM
  where sym is the symbol referring to the def.

  Class: source, non-standard

  Replacement requirements: see constraints"
  [nssym]
  (->> nssym
       find-ns
       ns-interns
       keys
       (map (fn [s] [nssym s]))))

(defconstrainedfn* deftup->defmap [grc/takes-symtup grc/returns-defmap]
  "Returns a partial metadata map for the def identified by [NSSYM DEFSYM].

  Class: transformer, non-standard

  Replacement: probably not necessary"
  [[nssym defsym]]
  {:name (name defsym)
   :level :grimoire.things/def
   :coords-suffix ["clj" (name nssym) (name defsym)]})

;; TODO: Handle defs that are not vars or correct constraints.
(defmulti merge-in-meta
  "Given a metadata map for a namespace or def, tries to find the namespace's or
  def's Cmetadata and assoces them under the key :cmeta. Returns metadata maps
  for any other kind of entity unchanged.

  Class: transformer"
  :level)

(defmethod merge-in-meta :grimoire.things/def
  [{[_ nsp-name def-name] :coords-suffix :as data}]
  (->> def-name
       symbol
       (ns-resolve (find-ns (symbol nsp-name)))
       meta
       (plumbing/assoc-when data :cmeta)))

(defmethod merge-in-meta :grimoire.things/namespace
  [{[_ nsp-name] :coords-suffix :as data}]
  (->> nsp-name
       symbol
       find-ns
       meta
       (plumbing/assoc-when data :cmeta)))

(defmethod merge-in-meta :default
  [data]
  data)

(trammel.provide/contracts
  [merge-in-meta "" grc/takes-defmap-or-nsmap-or-othermap]
  [merge-in-meta "" grc/returns-mdmap])

(defn complete-coords
  [{:keys [coords-suffix] :as ent-data} {artifact :name :keys [group version]}]
  (-> ent-data
      (dissoc :coords-suffix)
      (assoc :coords (into [group artifact version] coords-suffix))))

(def clj-entity-src-graph
  {:nssyms         (fnk* [dir-path] clj-nssym-src)
   :nsmaps         (fnk* [nssyms] (map nssym->nsmap))
   :deftups        (fnk* [nssyms] (mapcat nssym->deftups))
   :defmaps        (fnk* [deftups] (map deftup->defmap))
   :bare-ents      (fnk* [nsmaps defmaps] concat)
   :ents-with-meta (fnk* [bare-ents] (map merge-in-meta))
   :entity-maps (fnk [ents-with-meta artifact-coords]
                  (map #(complete-coords % artifact-coords)
                       ents-with-meta))})

(defn clj-entity-src [dir-path artifact-coords]
  (-> ((graph/eager-compile clj-entity-src-graph)
       {:dir-path dir-path, :artifact-coords artifact-coords})
      :entity-maps))
