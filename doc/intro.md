# The case of Dorothy the Documenter

(If you've found this part of the documentation by chance, but aren't sure
whether it is what you're looking for, go to the [overview](overview.md).)

If you've read the Clojure Gazette interview about lib-grenada during the Google
Summer of Code, you already know Dorothy the Documenter and her quest. This here
is a tutorial that guides you through the steps she would take. If you don't
know Dorothy the Documenter: in this tutorial I'll show you how to build a
Datadoc JAR with beginner documentation for the fns and variables in
`clojure.core`.


## Step 0: Prepare a Leiningen project

 1. Create a Leiningen project.
 2. Add the newest version of the following libraries to the dependencies in
    project.clj:
     - [lib-grenada](https://github.com/clj-grenada/lib-grenada/tree/master)
     - [poomoo](https://github.com/clj-grenada/poomoo)
     - [lib-grimoire](https://github.com/clojure-grimoire/lib-grimoire/tree/master)
 4. Change the Clojure version to 1.7.0.
 5. Start your favourite environment for evaluating Clojure code (probably some
    sort of REPL).


## Step 1: Get a Datadoc JAR and look what's in there

A Datadoc JAR is a JAR file with data documenting some Clojure code.
Evaluate the following. It will download a Datadoc JAR with documentation for
the core Clojure namespaces and read in the data.

```clojure
(require '[grenada.sources :as gr-sources])

(def data (gr-sources/from-depspec '[org.clojars.rmoehn/clojure "1.7.0+003"
                                     :classifier "datadoc"]))
```

You see that Datadoc JARs are specified in the same way as Leiningen
dependencies, which in turn are based on Maven coordinates. Now have a look at
the data.

```clojure
(require '[grenada.exporters.pretty :as e]
         '[grenada.converters :as gr-converters])

(def data-map (gr-converters/to-mapping data))

(e/pprint (get data-map ["org.clojure"]))
(e/pprint (get data-map ["org.clojure" "clojure"]))
(e/pprint (get data-map ["org.clojure" "clojure" "1.7.0"]))
(e/pprint (get data-map ["org.clojure" "clojure" "1.7.0" "clj"]))
(e/pprint (get data-map ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core"]))
(e/pprint (get data-map ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core" "concat"]))
```

Please have a good look at these and play a bit, also with `data` and
`data-map`, because it will prepare you for the next step…………


## Step 2: Get some background knowledge

Read [this
explanation](https://github.com/clj-grenada/grenada-spec/blob/devel/NewModel.md)
of Things, Aspects and Bars.

Checkpoint: by now you should have understood that we're dealing with Things.
They have coordinates that link them to concrete Clojure Things. They have
Aspects that tell about what a Thing represents and what it means. They have
Bars that hold arbitrary, but meaningful data. They are packaged up in Datadoc
JARs. (If you're very puzzled at this point, please tell me your problems and
I'll do my best to improve the documentation.)


## Step 3: Write additional documentation

You want to write beginner documentation for all the Things in clojure.core.
First you need to settle on an input format. For ease of editing, we settle on
one file per Thing, containing its coordinates, calling forms and doc string.
The markup language will be [CommonMark](http://commonmark.org/). Example for
the file `concat.md`:

    ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core" "concat"]

    Calling: [& xs]

    `concat` takes an arbitrary number of collections and returns a lazy
    sequence of their elements in order. – It *concat*enates its inputs into one
    lazy sequence. Example:

    ```clojure
    (concat [:a :b :c] (range 4))
    ;; => (:a :b :c 0 1 2 3)
    ```

    Note that you have to be careful with `concat`, as Stuart Sierra
    [explains](http://stuartsierra.com/2015/04/26/clojure-donts-concat).

You don't want to write out all these filenames and coordinates and argument
lists by hand, so you write some code that generates them:

```clojure
(require '[clojure.java.io :as io]
         '[clojure.string :as string]
         '[grenada.things :as t]
         '[grimoire.util :refer [munge]])

(def all-clojure-core
  (->> data
       (filter #(t/has-aspect? ::t/find %))
       (filter #(= "clojure.core" (get-in % [:coords 4])))))

(defn format-for-file [t]
  (str (:coords t) \newline
       \newline
       (if-let [calling (get-in t [:bars :grenada.bars/calling])]
         (str "Calling: " (string/join " " calling) \newline \newline)
         "")
       \newline))

(.mkdir (io/file "core-doc"))

(doseq [t all-clojure-core
        :let [path (-> t
                       :coords
                       last
                       munge
                       (as-> x (str "core-doc/" x ".md")))]]
  (assert (not (.exists (io/file path))))
  (spit path (format-for-file t)))
```

You also write some extra documentation for the namespace itself:

    ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core"]

    The functions, procedures, macros and other definitions in `clojure.core`
    constitute most of Clojure's functionality.

    …


## Step 4: Make Things out of the documentation you've written

You now have the beginner documentation on file, but you want to attach it to
Things, so that it can be packaged in a Datadoc JAR. In order to attach data to
a Thing, you have to put it in a Bar. I have defined a Bar type that fits this
purpose: `:poomoo.bars/docs` First you need to read and parse the documentation
files. Poomoo also helps you with that:

```clojure
(require '[grenada.bars :as b]
         '[poomoo bars parsing])

(defn insert-docs [things-map doc-map]
  (let [{:keys [coords calling contents]} doc-map
        thing (get things-map coords)]
    (assert (and coords contents thing))
    (as-> thing t
      (t/attach-bar poomoo.bars/def-for-bar-type
                    :poomoo.bars/docs
                    {"doros-docs" contents}
                    t)
      (if (t/has-bar? ::b/calling  t)
        (t/replace-bar b/def-for-bar-type ::b/calling calling t)
        t)
      (assoc things-map coords t))))

(def things-with-docs-map
  (->> "core-doc"
       io/file
       file-seq
       rest ; Throws out the directory itself.
       (map slurp)
       (map poomoo.parsing/parse-ext-doc-string)
       (reduce insert-docs data-map)))
```

This is the manual way of attaching Bars and it assumes that the Thing doesn't
have a `:poomoo.bars/doc` Bar already. Later there will be the possibility of
merging two Things with the same coordinates together, automatically combining
their Bars.

Let's see if our new docs were attached:

```clojure
(e/pprint (get things-with-docs-map ["org.clojure" "clojure" "1.7.0" "clj"]))
(e/pprint (get things-with-docs-map ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core"]))
(e/pprint (get things-with-docs-map ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core" "concat"]))
```

## Step 5: Verify the code examples

You also want to make sure that the code examples you included with the
additional docs actually do what you say they do. Poomoo contains some primitive
procedures that check examples if they're written as
[above](#step-3-write-additional-documentation).

```clojure
(require '[poomoo.doctest :as doctest])

(doseq [[coords thing] things-with-docs-map
        :when (t/has-bar? :poomoo.bars/docs thing)
        :let [doc (get-in thing [:bars :poomoo.bars/docs "doros-docs"])
              check-res (poomoo.doctest/check-examples (str doc \newline))]
        :when (seq check-res)]
  (println "Failed check.")
  (e/pprint thing)
  (e/pprint check-res))
```

If all your examples are correct, it prints nothing. Not the most user-friendly
interface, but good enough for private checks. And don't puzzle over why we're
adding a newline; it just works around a shortcoming of the parser.

 - What should the reader be able to do?
     - Understand the Grenada format.
       - Know what Things are.
       - Be able to locate a Thing using coordinates.
       - Know what Aspects are.
           - Know the main Aspects.
           - Be able to find information about what Aspects mean.
           - Know where to find information about defining their own Aspects.
       - Know what Bars and Bar types are.
           - Be able to find information about Bars.
           - Know where to find information about defining their own Aspects.
           - Be able to query Bars on Things.
           - Be able to attach to and remove Bars from Things.
       - Be able to programmatically download Datadoc JARs.
       - Be able to read the data from Datadoc JARs.
       - Be able to pack Datadoc JARs.
       - Be able to programmatically deploy Datadoc JARs.
       - Be able to merge two collections of Things.

 - Start off with something that gets the user going quickly.
    - ✰ Specify a dependency thing.
       - ✰ Grenada data is packaged in Datadoc JARs.
       - This will be the core Clojure docs from Grimoire with only coordinates,
         Aspects, arg lists (for fns and macros) and doc strings. Say,
         [org.clojars.rmoehn/core-clojure "1.7.0" :classifier datadoc]
       - ✰ Will download something from the internet!
       - ✰ Understand: Datadoc JAR dependencies are specified in the same way as
         for Leiningen, which in turn uses Maven coordinates.
       - If you don't know how Leiningen dependencies work:
         https://github.com/technomancy/leiningen/blob/master/doc/TUTORIAL.md#dependencies
         However, this is quite bad. In short:
          - The part before the slash is the groupId.
          - The part after the slash is the artifactId.
          - If there is only one term without a slash, it's both groupId and
            artifactId.
          - Last part is the version.
          - Can have a classifier: "datadoc" classifier means that it only
            contains Grenada format data. No classifier means that it might
            contain Grenada format data, but in most cases not.
          - More information on Maven coordinates:
            https://maven.apache.org/pom.html#Maven_Coordinates
       - Datadoc JAR has coordinates, but can contain data about projects with
         completely different coordinates.
       - ✰ Strange "org.clojars.rmoehn", because as of now not many Datadoc JARs
         out there and I uploaded this one for teaching purposes.
    - ✰ Have that Datadoc JAR downloaded and read some contents from it.
       - ✰ Let's read something from the JAR.
       - ✰ Get the Thing "org.clojure". – Just the code for it without
         explanation.
       - We get something like a map with the entries :coordinates, :aspects and
         :bars. (Appears not to be a normal map, but we'll get to that later.
         For now, just see it as a map.)
       - This is the representation of a Thing.
       - A Thing holds data about concrete things in Clojure space.
       - There are different types of concrete things, some everyday matter like
         fns and namespaces. Others equally fundamental but less present like
         Maven groups, artifacts, versions.
       - All organized in a hierarchy.
       - ✰ Find examples of a Thing at every point in the hierarchy. –
         "org.clojure" "clojure" "1.7.0" "clj" "clojure.core" "map".
       - ✰ Now play around with this and guess a bit about what all this means.
         (Please do, it's part of my concept.*
         *http://devchat.tv/freelancers/164-fs-teaching-and-learning-courses-wit-breanne-dyck
         (but can't find the original study :-().
       - Also play around with data and data-map.
       - ✰ Now read the spec. (Only link, not the word "spec"!)
     - Checkpoint: by now you should have understood that we're dealing with
       Things. They have coordinates that link them to concrete Clojure Things.
       They have Aspects that tell about what a Thing represents and what it
       means. They have Bars that hold arbitrary, but meaningful data. They are
       packaged up in Datadoc JARs.
     - Now, want to write beginner documentation for all the Things in
       clojure.core. Will give two examples.
        - When we want to write about some concrete Clojure thing, we have to
          identify it by its coordinates, so that Grenada knows what we're
          talking about.
        - Settle on a format for documentation that is one file per Thing and
          the coordinates at the top:

              ["org.clojure" "clojure" "1.7.0" "clj" "core.core" "map"]

              Arg lists: …

              …

        - We can write a short snippet to generate such files for all Finds in
          clojure.core.
        - Can also write documentation for a namespace:

              ["org.clojure" "clojure" "1.7.0" "clj" "core.core"]
              …

        - Package this up into a collection of Things. – A Bar with some AST
          and the original data each.
           - In case you want to attach some data and there is no Bar type that
             fits: define your own as described in … – It's not hard.
        - Check that the examples are correct.
        - Maybe transform into a new Bar.
        - Merge back together with the old data.
     - At this point we have a data structure with the old and the new data
       combined.
        - For packaging it, you need to choose coordinates. – Coordinates of
          Datadoc JARs are independent of the group/artifact/version coordinates
          of the data they contain.
        - For experimentation, choose your very own group on Clojars. artifactId
          can be whatever, "beginner-docs". Version might make sense to match up
          with Clojure and add a revision number for your docs: "1.7.0+3". – But
          all that is up to you.
        - With that in hand, create the JAR.
        - Also load it and – just for fun – read one Thing from it.
        - Deploy it to Clojars easily.


 - What about the Paul deGrandis case? Can get some distance with the spec. Need
   to fill the rest, too.
    - At the top of the file: if you're the kind of person who likes discovering
      something from little bits and examples, read on. If you're the kind of
      person who likes to learn the abstract concepts first, have a look at the
      Grenada spec and ….
