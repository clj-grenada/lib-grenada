(ns grenada-lib.sources.clj
  "Procedures for extracting metadata from Clojure source trees and JAR files."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [grenada-lib.config :refer [config]]
            [grenada-lib.util :as gr-util :refer [fnk* defconstrainedfn*]]
            [grenada-lib.sources.contracts :as grc]
            [grenada-lib.reading :as reading]
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

;;; TODO: The whole thing with the separate runtime made it a lot more difficult
;;;       for people to write .clj extractors. Think about how either to make
;;;       the stuff in this namespace extensible or to provide an API that makes
;;;       writing new extractors reasonably easy. (RM 2015-06-19)
;;; TODO: Think about what should be private and what should be public in this
;;;       namespace. (RM 2015-06-24)

;;;; Schemas

(def LeinDepSpec
  (s/either [(s/one s/Symbol "group-artifact") (s/one s/Str "version")]
            [(s/one s/Symbol "group-artifact") (s/one s/Str "version")
             (s/one (s/eq :classifier) "cl-key") (s/one s/Str "classifier")]))


;;;; Helpers

(defn- string-array [& args]
  (into-array String args))

(s/defn ^:always-validate resolve-specs
  "

  If you find yourself calling this procedure twice and then concat-ing the
  results, you've found yourself getting into trouble. The dependency resolution
  mechanism needs to see all dependency specifications at once in order to work
  correctly. See also the implementation comment on get-extraction-shim."
  [& dep-specs :- [LeinDepSpec]]
  (aether/dependency-files
    (aether/resolve-dependencies
      :coordinates dep-specs
      :repositories (safe-get config :default-repositories))))

(s/defn resolve-artifact [dep-spec :- LeinDepSpec]
  (let [files (map #(safe-get (meta %) :file)
                   (aether/resolve-artifacts
                     :coordinates [dep-spec]
                     :repositories (safe-get config :default-repositories)))]
    (when (zero? (count files))
      (throw (IllegalArgumentException. (str "Couldn't resolve dependency"
                                             dep-spec))))
    (when (> (count files) 1)
      (throw (AssertionError.
               "A dep-spec shouldn't resolve to more than one file.")))
    files))

(defn create-file-seq [where-to-look]
  (cond
    (and (sequential? where-to-look)
         (instance? java.io.File (first where-to-look)))
    where-to-look

    (and (vector? where-to-look) (symbol? (first where-to-look)))
    (resolve-artifact (s/validate LeinDepSpec where-to-look))

    (instance? java.io.File where-to-look)
    [where-to-look]

    :default
    (throw (IllegalArgumentException.
             (str where-to-look " is not a valid thing to search for entities."
                  " Maybe you forgot to wrap it in a File?")))))

;; Attention when changing the code:
;;
;; If you don't know about Maven dependency resolution and wonder why we're
;; doing things the way we're doing them, go away and read and understand
;; everything before you change anything. Especially you need to understand why
;; we feed all the depspecs to resolve-specs at once and why this is absolutely
;; necessary. In short, it is absolutely necessary, because the Maven dependency
;; resolution mechanism needs to see all the dependencies together in order to
;; work correctly. If you run it with some dependencies first and then with some
;; other dependencies and then just concat the resulting collections of files,
;; you will likely end up with two versions of the same artifact on the
;; classpath. And not notice it. – Very bad.
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
;;
;; TODO: Provide a slot for some little extractor library. Of course it can be
;;       included in the files, but it would be better to make it official and
;;       warn people not to introduce dependencies that would scramble up
;;       everything. (RM 2015-06-19)
;; TODO: Support creating shims with a local Leiningen project loaded. (RM
;;       2015-06-21)
;; TODO: Support creating shims with a local JAR loaded. (RM 2015-06-21)
(s/defn get-extraction-shim
  "Creates a new Clojure runtime with only the Clojure according to CLJ-DEPSPEC
  and the JARs behind WHERE-TO-LOOK-DEPSPEC loaded.

  Note that currently this only supports creating runtimes with files according
  to Leiningen dependency specs loaded. "
  [clj-depspec :- LeinDepSpec where-to-look-depspec]
  (let [jars-to-load (resolve-specs  ; See comment above!
                       clj-depspec
                       ['org.projectodd.shimdandy/shimdandy-impl
                        (safe-get config :shimdandy-version)]
                       ['org.projectodd.shimdandy/shimdandy-api
                        (safe-get config :shimdandy-version)]
                       where-to-look-depspec)
        cleanroom-dir (io/resource "clj/")

        class-ldr
        (URLClassLoader.
          (into-array URL (map io/as-url (conj jars-to-load cleanroom-dir)))
          (.getParent (ClassLoader/getSystemClassLoader)))

        runtime-shim-class
        (.loadClass class-ldr "org.projectodd.shimdandy.ClojureRuntimeShim")

        new-runtime-method
        (.getMethod runtime-shim-class
                    "newRuntime"
                    (into-array Class [ClassLoader String]))
        runtime
        (.invoke new-runtime-method
                 nil (object-array [class-ldr "Grenada capturing"]))]
    (.require runtime (string-array "clojure.core"
                                    "grenada-lib.cleanroom"))
    runtime))


;;;; Non-standard transformers that don't require a separate runtime

(defconstrainedfn* nssym->nsmap [grc/takes-sym grc/returns-nsmap]
  "Returns a partial metadata map for the namespace denoted by S.

  See the code for what this means.

  Class: transformer, non-standard

  Replacement: probably not necessary"
  [s]
  {:name (name s)
   :level :grimoire.things/namespace
   :coords-suffix ["clj" (name s)]})

(defconstrainedfn* deftup->defmap [grc/takes-symtup grc/returns-defmap]
  "Returns a partial metadata map for the def identified by [NSSYM DEFSYM].

  Class: transformer, non-standard

  Replacement: probably not necessary"
  [[nssym defsym]]
  {:name (name defsym)
   :level :grimoire.things/def
   :coords-suffix ["clj" (name nssym) (name defsym)]})

(defn complete-coords
  [{:keys [coords-suffix] :as ent-data} {artifact :name :keys [group version]}]
  (-> ent-data
      (dissoc :coords-suffix)
      (assoc :coords (into [group artifact version] coords-suffix))))


;;;; Non-standard transformers that require a separate runtime

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

(defn merge-in-ns-meta-in-rt [rt]
  (fn [{[_ nsp-name] :coords-suffix :as data}]
    (->> nsp-name
         (.invoke rt "grenada-lib.cleanroom/ns-meta")
         reading/read-string
         (plumbing/assoc-when data :cmeta))))

(defn merge-in-def-meta-in-rt [rt]
  (fn [{[_ nsp-name def-name] :coords-suffix :as data}]
    (->> (symbol nsp-name def-name)
         str
         (.invoke rt "grenada-lib.cleanroom/var-meta")
         reading/read-string
         (plumbing/assoc-when data :cmeta))))


;;;; Public API

;;; TODO: Dissoc :extensions from :cmeta and merge with toplevel :extensions.
;;; TODO: Add a convenience procedure for extracting metadata from
;;;       clojure-* specs. (RM 2015-06-23)
;;; TODO: Add a convenience procedure that automatically fills in the
;;;       :artifact-coords when given a depspec for :where-to-look. (RM
;;;       2015-06-19)
;;; TODO: Add another convenience procedure that automatically fills in the
;;;       :artifact-coords when given a JAR file for :where-to-look. (RM
;;;       2015-06-19)

(def clj-entity-src-graph
  "

  You're not supposed to modify this graph, but inspecting it is okay.

  We're using tools.namespace/find-namespaces for extracting namespace-naming
  symbols from the files from :where-to-look. We load the namespaces into a
  separate Clojure runtime. All namespaces will be loaded together. In order to
  make the loading order deterministic, we sort the namespace-naming symbols
  with clojure.core/sort before requiring them into the runtime one after the
  other.

  Input nodes: :where-to-look :clj-depspec :artifact-coords"
  {:files
   (fnk* [where-to-look] create-file-seq)

   :nssyms
   (fnk [files] (sort (tns.find/find-namespaces files)))

   :bare-runtime
   (fnk* [clj-depspec where-to-look] get-extraction-shim)

   :runtime
   (fnk [bare-runtime nssyms]
     (.require bare-runtime (apply string-array (map str nssyms)))
     bare-runtime)

   :nsmaps
   (fnk* [nssyms] (map nssym->nsmap))

   :deftups
   (fnk [runtime nssyms] (mapcat (nssym->deftups-in-rt runtime) nssyms))

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

;; TODO: Check that the user didn't provide a *.clj File. (RM 2015-06-19)
;; TODO: Build support for the stuff that is not Leiningen dependency specs,
;;       remove the first paragraph of the doc string and correct the second
;;       paragraph. (RM 2015-06-21)
(s/defn ^:always-validate clj-entity-src
  "

  IMPORTANT NOTE: Ignore the following paragraph. What is written there will be
  supported again in a similar way. Right now, however, WHERE-TO-LOOK must be a
  Leiningen-style dependency spec.

  WHERE-TO-LOOK can either be a JAR File or a directory File. If it is a
  directory, it has to be the root of the tree of Clojure files to scan. That
  means, if you want to extract metadata from cool/namespace.clj, you can't just
  provide /path/to/cool/namespace.clj. You have to provide /path/to/ (including
  the trailing slash!), because otherwise Clojure won't be able to find the
  file. Don't ask me why. If it is required that you only extract metadata from
  that one namespace, send me a message and I will figure it out. Of course you
  can modify the graph to include a filter, but then it's your responsibility,
  not mine. I warned you.

  READ THE FOLLOWING! or you're in for trouble.

  If the artifact you want to extract metadata from is some version of Clojure
  itself, you have to provide the same version of Clojure in CLJ-DEPSPEC.
  Otherwise there is no guarantee that you will be extracting the right things.
  Right now, we don't check if you obey this rule and it is detrimental to you
  if you don't. So make extra sure you specified the right things.

  Extracting metadata from versions of Clojure before 1.7.0-RC1 is not supported
  yet."
  [clj-depspec :- LeinDepSpec where-to-look artifact-coords]
  (safe-get ((graph/eager-compile clj-entity-src-graph)
             {:clj-depspec clj-depspec
              :where-to-look where-to-look
              :artifact-coords artifact-coords})
            :entity-maps))


;;;; Outdated comments

;;; These notes have to do with the contracts and how to customize this entity
;;; source in the proper way. I probably won't allow this flexibility, but for
;;; now the contracts stuff is still in here, so I'll also leave the comments.
;;;
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
