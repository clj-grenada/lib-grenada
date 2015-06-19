(ns grenada-lib.sources.clj
  "Procedures for extracting metadata from .clj files."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [grenada-lib.util :as gr-util :refer [fnk* defconstrainedfn*]]
            [grenada-lib.sources.contracts :as grc]
            [medley.core :refer [dissoc-in]]
            [clojure.tools.namespace.find :as tns.find]
            [schema.core :as s]
            [clojure.pprint :refer [pprint]]
            [plumbing.core :as plumbing :refer [fnk safe-get]]
            [plumbing.graph :as graph]
            [cemerick.pomegranate.aether :as aether])
  (:import java.io.File
           java.util.regex.Pattern
           java.util.jar.JarFile
           [java.net URL URLClassLoader]
           org.reflections.util.ClasspathHelper))

(def LeinDepSpec
  (s/either [(s/one s/Symbol "group-artifact") (s/one s/Str "version")]
            [(s/one s/Symbol "group-artifact") (s/one s/Str "version")
             (s/one (s/eq :classifier) "cl-key") (s/one s/Str "classifier")]))

;;; TODO: Make the following defs configurable.

;; Credits: https://github.com/boot-clj/boot/blob/c244cc6cffea48ce2912706567b3bc41a4d387c7/boot/aether/src/boot/aether.clj
(def default-repositories {"clojars"       "https://clojars.org/repo/"
                           "maven-central" "https://repo1.maven.org/maven2/"})

(def shimdandy-v "1.1.0")

(s/defn resolve-spec [dep-spec :- LeinDepSpec]
  (let [files (aether/dependency-files
                (aether/resolve-dependencies
                  :coordinates [dep-spec]
                  :repositories default-repositories))]
    (when (zero? (count files))
      (throw (IllegalArgumentException. (str "Couldn't resolve dependency"
                                             dep-spec))))
    files))

(defn create-file-seq [where-to-look]
  (cond
    (and (sequential? where-to-look)
         (instance? java.io.File (first where-to-look)))
    where-to-look

    (and (vector? where-to-look) (symbol? (first where-to-look)))
    (resolve-spec (s/validate LeinDepSpec where-to-look))

    (instance? java.io.File where-to-look)
    [where-to-look]

    :default
    (throw (IllegalArgumentException.
             (str where-to-look " is not a valid thing to search for entities."
                  " Maybe you forgot to wrap it in a File?")))))

;; TODO: Provide a slot for some little extractor library. Of course it can be
;; included in the files, but it would be better to make it official and warn
;; people not to introduce dependencies that would scramble up everything.
;;
;; About the implementation:
;;
;; The SystemClassLoader is able to load anything from the classpath and will do
;; so if you ask it. We don't want do have any other files than those specified
;; in our Clojure runtime, since that might result in the wrong files being
;; scraped. Thus, when constructing the URLClassLoader, we can't let it use the
;; default parent, since that would most likely be the SystemClassLoader.
;; Therefore we use the parent of the SystemClassLoader, which doesn't load
;; anything from the classpath.
;;
;; Now, however, we can't just :import
;; org.projectodd.shimdandy.ClojureRuntimeShim and use
;; ClojureRuntimeShim/newRuntime. It would be loaded with Clojure's
;; ContextClassLoader and internally load ClojureRuntimeShimImpl loaded by our
;; custom URLClassLoader and try to upcast that to ClojureRuntimeShim. That
;; wouldn't work, because ClojureRuntimeShim and ClojureRuntimeShimImpl would
;; have been loaded by different classloaders. Cross-ClassLoader casting is not
;; possible. Therefore we use our custom URLClassLoader to load both
;; ClojureRuntimeShim and ClojureRuntimeShimImpl, which is why we have to do
;; some reflection stuff.
(s/defn get-extraction-shim
  "Creates a new Clojure runtime with only the Clojure according to CLJ-DEPSPEC
  and the FILES loaded."
  [clj-depspec :- LeinDepSpec files]
  (let [clojure-jar (resolve-spec clj-depspec)
        shim-impl-jar (resolve-spec
                        ['org.projectodd.shimdandy/shimdandy-impl shimdandy-v])
        shim-api-jar (resolve-spec
                       ['org.projectodd.shimdandy/shimdandy-api shimdandy-v])
        cleanroom-file [(io/resource "clj/")]

        class-ldr
        (URLClassLoader.
          (into-array URL (map io/as-url
                               (concat shim-api-jar
                                       shim-impl-jar
                                       clojure-jar
                                       cleanroom-file
                                       files)))
          (.getParent (ClassLoader/getSystemClassLoader)))

        runtime-shim-class
        (.loadClass class-ldr "org.projectodd.shimdandy.ClojureRuntimeShim")

        new-runtime-method
        (.getMethod runtime-shim-class
                    "newRuntime"
                    (into-array Class [ClassLoader String]))]
    (.invoke new-runtime-method
             nil (object-array [class-ldr "Grenada capturing"]))))

(defn string-array [& args]
  (into-array String args))

(defn symbol-in-rt [rt sym]
  (.invoke rt "clojure.core/symbol" (str sym)))

(defn find-ns-in-rt
  "
  Necessary because symbols from here can't be used in the other runtime or
  something."
  [rt nssym]
  (->> nssym
       (symbol-in-rt rt)
       (.invoke rt "clojure.core/find-ns")))

(defn ns-resolve-in-rt [rt rt-ns defsym]
  (->> defsym
       (symbol-in-rt rt)
       (.invoke rt "clojure.core/ns-resolve" rt-ns)))

;; - Doing the namespace removing for true order-independence. – I don't know
;;   if it's necessary, though.
(defn get-meta-in-runtime
  "Expects the namespace denoted by NSSYM to be loaded in RT."
  [rt nssym]
  (.invoke rt "clojure.core/meta" (find-ns-in-rt rt nssym)))

(defconstrainedfn* nssym->nsmap [grc/takes-sym grc/returns-nsmap]
  "Returns a partial metadata map for the namespace denoted by S.

  See the code for what this means.

  Class: transformer, non-standard

  Replacement: probably not necessary"
  [s]
  {:name (name s)
   :level :grimoire.things/namespace
   :coords-suffix ["clj" (name s)]})

(defn nssym->deftups-in-rt
  "
  Deftups are easier to destructure than namespace-qualified symbols."
  [rt]
  (fn [nssym]
    (->> nssym
         str
         (.invoke rt "grenada-lib.cleanroom/ns-interns-strs")
         (map symbol)
         (map (fn [s] [nssym s])))))

(defconstrainedfn* deftup->defmap [grc/takes-symtup grc/returns-defmap]
  "Returns a partial metadata map for the def identified by [NSSYM DEFSYM].

  Class: transformer, non-standard

  Replacement: probably not necessary"
  [[nssym defsym]]
  {:name (name defsym)
   :level :grimoire.things/def
   :coords-suffix ["clj" (name nssym) (name defsym)]})

(defn merge-in-ns-meta-in-rt [rt]
  (fn [{[_ nsp-name] :coords-suffix :as data}]
    (->> nsp-name
         (.invoke rt "grenada-lib.cleanroom/ns-meta")
         (edn/read-string {:default (constantly :unknown-object-ignored)})
         (plumbing/assoc-when data :cmeta))))

(defn merge-in-def-meta-in-rt [rt]
  (fn [{[_ nsp-name def-name] :coords-suffix :as data}]
    (->> (symbol nsp-name def-name)
         str
         (.invoke rt "grenada-lib.cleanroom/var-meta")
         (edn/read-string {:default (constantly :unknown-object-ignored)})
         (plumbing/assoc-when data :cmeta))))

(defn complete-coords
  [{:keys [coords-suffix] :as ent-data} {artifact :name :keys [group version]}]
  (-> ent-data
      (dissoc :coords-suffix)
      (assoc :coords (into [group artifact version] coords-suffix))))

; - We might get a JAR file, a directory, a .clj file, a .cljc file, a dependency
;   spec.
; - Files alone don't work with the URLClassLoader or require. Don't know what
;   is the reason. You have to specify the directory that contains the
;   directories according to the namespace. So, if you want to require namespace
;   my.namespace somewhere, you can't just put /path/to/my/namespace.clj in the
;   classloader. You have to put /path/to/. And the trailing slash is required.
; - For everything but the dependency spec we can create a file-seq and run
;   tns.find/find-namespaces on it.
; - For the dependency spec we have resolve-dependencies (Pomegranate) it first
;   and get the files and then we can run tffn.
; - Then we have the namespaces.
; - The files from before we place in the :source-paths of the Boot environment.
; - We place the specified Clojure version in :dependencies of the Boot
;   environment.
; - We then start the Pod, require the namespaces one by one and run a bit of
;   code in the pod that captures the necessary metadata and remove-ns the
;   namespace again and return the metadata.
; - Okay, now

;; - Sort namespace symbols so that the order of loading becomes deterministic.
(s/defn ^:always-validate other-old-entity-src [clj-depspec :- LeinDepSpec where-to-look]
  (let [files (create-file-seq where-to-look)
        nss (sort (tns.find/find-namespaces files))
        _ (println nss)
        runtime (get-extraction-shim clj-depspec files)
        _ (.require runtime (string-array "clojure.core"))
        _ (.require runtime (apply string-array (map str nss)))
        ns-metas (map #(get-meta-in-runtime runtime %) nss)]
    ns-metas))

;; Input nodes: :where-to-look :clj-depspec :artifact-coords
(def clj-entity-src-graph
  {:files
   (fnk* [where-to-look] create-file-seq)

   :nssyms
   (fnk [files] (sort (tns.find/find-namespaces files)))

   :bare-runtime
   (fnk* [clj-depspec files] get-extraction-shim)

   :runtime
   (fnk [bare-runtime nssyms]
     (.require bare-runtime (string-array "clojure.core"
                                          "grenada-lib.cleanroom"))
     (.require bare-runtime (apply string-array (map str nssyms)))
     bare-runtime)

   :nsmaps
   (fnk* [nssyms] (map nssym->nsmap))

   :deftups
   (fnk [runtime nssyms] (mapcat (nssym->deftups-in-rt runtime)
                                 nssyms))

   :defmaps
   (fnk* [deftups] (map deftup->defmap))

   :nsmaps-with-meta
   (fnk [runtime nsmaps] (map (merge-in-ns-meta-in-rt runtime)
                              nsmaps))

   :defmaps-with-meta
   (fnk [runtime defmaps]
     (map (merge-in-def-meta-in-rt runtime)
          defmaps))

   :ents-with-meta
   (fnk* [nsmaps-with-meta defmaps-with-meta] concat)

   :entity-maps
   (fnk [ents-with-meta artifact-coords]
     (map #(complete-coords % artifact-coords) ents-with-meta))})

(s/defn ^:always-validate clj-entity-src
  [clj-depspec :- LeinDepSpec where-to-look artifact-coords]
  ((graph/lazy-compile clj-entity-src-graph)
             {:clj-depspec clj-depspec
              :where-to-look where-to-look
              :artifact-coords artifact-coords}))

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
(defconstrainedfn* clj-dir-nssym-src [grc/takes-dir grc/nssym-src]
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

(defn clj-jar-nssym-src [jar-file]
  (tns.find/find-namespaces-in-jarfile jar-file))

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

(s/defn spec->artifact-coords [spec :- LeinDepSpec]
  (let [[group-artifact version & _] spec
        artifact (name group-artifact)
        group (or (namespace group-artifact) artifact)]
    {:group group
     :artifact artifact
     :version version}))

(s/defn spec->relpath [spec :- LeinDepSpec]
  (let [[_ _ _ ?classifier] spec
        {:keys [artifact group version]} (spec->artifact-coords spec)
        classifier (if ?classifier (str "-" ?classifier) "")
        jar-name (str artifact "-" version classifier ".jar")]
    (io/file group artifact version jar-name)))

;; TODO: Warning or exception when there is more than one file.
(s/defn find-in-classpath [spec :- LeinDepSpec]
  (let [jar-path-re (-> spec
                        spec->relpath
                        str
                        Pattern/quote
                        re-pattern)
        files (->> [(ClasspathHelper/contextClassLoader)]
                   (into-array ClassLoader)
                   ClasspathHelper/forClassLoader
                   (filter #(re-find jar-path-re (str %))))]
    (when (> 1 (count files))
      (gr-util/warn "More than one file conforming to" spec "on classpath."
                    "Either you have a weird classpath or the author of Grenada
                     has overlooked something."))
    (io/as-file (first files))))

(def old-clj-entity-src-graph
  {:nsmaps         (fnk* [nssyms] (map nssym->nsmap))
   :deftups        (fnk* [nssyms] (mapcat nssym->deftups))
   :defmaps        (fnk* [deftups] (map deftup->defmap))
   :bare-ents      (fnk* [nsmaps defmaps] concat)
   :ents-with-meta (fnk* [bare-ents] (map merge-in-meta))
   :entity-maps (fnk [ents-with-meta artifact-coords]
                  (map #(complete-coords % artifact-coords)
                       ents-with-meta))})

(defn dir-entity-src [dir-path artifact-coords]
  (-> ((graph/eager-compile clj-entity-src-graph)
       {:dir-path dir-path
        :artifact-coords artifact-coords
        :nssyms         (fnk* [dir-path] clj-dir-nssym-src)})
      :entity-maps))

(s/defn ^:always-validate jar-entity-src [spec :- LeinDepSpec]
  (if-let [jar-file-file (find-in-classpath spec)]
    (-> ((graph/eager-compile (assoc clj-entity-src-graph
                                     :nssyms
                                     (fnk* [jar-file] clj-jar-nssym-src)))
         {:jar-file (JarFile. jar-file-file)
          :artifact-coords (spec->artifact-coords spec)})
        :entity-maps)
    (throw (IllegalArgumentException. (str "No JAR conforming to " spec
                                           " found on classpath.")))))
