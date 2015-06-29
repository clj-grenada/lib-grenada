(ns grenada.sources.clj
  "Procedures for extracting metadata from Clojure source trees and JAR files."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [grenada.config :refer [config]]
            [grenada.util :as gr-util :refer [fnk* defconstrainedfn*]]
            [grenada.sources.contracts :as grc]
            [grenada.reading :as reading]
            [grenada.things :as t]
            [clojure.tools.namespace.find :as tns.find]
            [schema.core :as s]
            [clojure.pprint :refer [pprint]]
            [plumbing.core :as plumbing :refer [fnk safe-get]]
            [plumbing.graph :as graph]
            [plumbing.map :as map]
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

(defn- dissoc-in*
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


(defn constructor-for-def [defmap]
  (cond
    (get defmap :macro)
    t/->macro

    (and (not (get defmap :macro) (get defmap :arglists)))
    t/->fn

    :default
    t/->plain-def))

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
                                    "grenada.cleanroom"))
    runtime))


;;;; Non-standard transformers that don't require a separate runtime

(defn move-extensions
  "

  Due to nil cases working together well, this also works for Things that don't
  have Cmetadata. There it just does nothing."
  [raw-thing]
  (let [cmeta-extensions (get-in raw-thing [:cmeta :grenada.cmeta/extensions])
        extensions (safe-get raw-thing :extensions)]
    (-> raw-thing
        (dissoc-in* [:cmeta :grenada.cmeta/extensions])
        (assoc :extensions (map/merge-disjoint extensions cmeta-extensions)))))

(defn symbol->str-vec [sym]
  (mapv str [(namespace sym) (name sym)]))

;; Since we're not inside the runtime, we can't use reflection to find out these
;; things.
;;
;; TODO: Look in the Java spec if this is the correct way.
(defn str->class-vec [qualified-classname]
  (let [[match nsstr classnm] (re-matches #"(?xms) \A (.+) [.] ([^.]+) \z"
                                          qualified-classname)]
    (assert match)
    [nsstr classnm]))

;; TODO: Implement this properly as soon as we've defined how the names of
;;       defmultis should look. (RM 2015-06-30)
(defn defmethod-coords [[_ {qnm :qualified-name} :as thing]]
  [(-> qnm symbol namespace str) "a-defmethod"])

(defn coords-in-platform [[_ {qnm :qualified-name} :as thing]]
  {:pre [qnm]}
  (cond
    ((some-fn t/var-backed? t/special-?) thing)
    (-> qnm symbol symbol->str-vec)

    (t/namespace-? thing)
    [qnm]

    (and (t/class-backed? thing) (not (t/var-backed? thing)))
    (str->class-vec qnm)

    (t/defmethod-? thing)
    (defmethod-coords thing)

    :default
    (throw (AssertionError. "This should not happen. I missed a case."))))

;; REFACTOR: This (except the "clj") and its helpers should go somewhere else.
;;           Maybe grenada.things? Not sure. (RM 2015-06-30)
(defn adjust-name-and-coords [{:keys [group artifact version]}]
  {:pre [artifact group version]}
  (fn [thing]
    (let [coords (into [artifact group version "clj"]
                       (coords-in-platform thing))]
      (-> thing
          (dissoc :qualified-name)
          (assoc :name (last coords))
          (assoc :coords coords)))))


;;;; Non-standard transformers that require a separate runtime

;; TODO: Add something that finds non-vars. (RM 2015-06-19)
(defn nsstr->var-def-vecs-in-rt [rt]
  (fn [nsstr]
    (->> nsstr
         (.invoke rt "grenada.cleanroom/ns-interns-strs")
         (map #(vector :var-def %)))))

(defn get-raw-thing-in-rt [rt]
  (s/fn [thing-vec :- [(s/one s/Keyword "kind") (s/one s/Str "name")]]
    (as-> thing-vec x
      (pr-str x)
      (.invoke rt "grenada.cleanroom/data-str-for" x
               (pr-str t/things-in-has-cmeta))
      (reading/read-string x)
      (t/vec->thing- x)
      (move-extensions x))))


;;;; Public API

;;; TODO: Add a convenience procedure for extracting metadata from
;;;       clojure-* specs. (RM 2015-06-23)
;;; TODO: Add a convenience procedure that automatically fills in the
;;;       :artifact-coords when given a depspec for :where-to-look. (RM
;;;       2015-06-19)
;;; TODO: Add another convenience procedure that automatically fills in the
;;;       :artifact-coords when given a JAR file for :where-to-look. (RM
;;;       2015-06-19)

;; TODO: Add a step that retrieves extension metadata from metadata annotations.
;;       (RM 2015-06-30)
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
  {;;; Finding namespaces to scrutinize

   :files
   (fnk* [where-to-look] create-file-seq)

   :nsstrs
   (fnk [files] (->> (sort (tns.find/find-namespaces files))
                     (map str)))

   :nsvecs
   (fnk* [nsstrs] (map #(vector :grenada.things/namespace %)))

   ;;; Assembling the separate runtime

   :bare-runtime
   (fnk* [clj-depspec where-to-look] get-extraction-shim)

   :runtime
   (fnk [bare-runtime nsstrs]
     (.require bare-runtime (apply string-array nsstrs))
     bare-runtime)

   ;;; Finding out what is defined in the namespaces

   :var-def-vecs
   (fnk [runtime nsstrs] (mapcat (nsstr->var-def-vecs-in-rt runtime) nsstrs))

   ;;; Asking the runtime about all the Things we found

   :thing-vecs
   (fnk* [nsvecs var-def-vecs] concat)

   :raw-things
   (fnk [runtime thing-vecs]
     (map (get-raw-thing-in-rt runtime) thing-vecs))

   ;;; Splicing in coordinates

   :things-with-complete-coords
   (fnk [artifact-coords raw-things]
     (map (adjust-name-and-coords artifact-coords) raw-things))

   ;;; Final check

   :things
   (fnk [things-with-complete-coords]
     (assert (every? t/thing? things-with-complete-coords))
     things-with-complete-coords)})

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
            :things))


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
