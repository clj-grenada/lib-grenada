# The case of Dorothy the Documenter – Compiling Datadoc JARs in 27 easy steps

Actual there are only eight steps, so don't worry.

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
```

<!-- Fenced code blocks with more than three backticks will be stripped away by
     strip-markdown. -->
```````clojure
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
```````

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
(require '[grenada
           [bars :as b]
           [utils :as gr-utils]]
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
  (let [pre (->> "core-doc"
                 io/file
                 gr-utils/ordinary-file-seq
                 (map slurp)
                 (pmap poomoo.parsing/parse-ext-doc-string)
                 (reduce insert-docs data-map))]
    (update pre
            ["org.clojure" "clojure" "1.7.0" "clj" "clojure.core"]
            #(t/attach-bar poomoo.bars/def-for-bar-type
                           :poomoo.bars/docs-markup-all
                           :common-mark
                           %))))
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

```````clojure
(require '[poomoo.doctest :as doctest])

(doseq [[coords thing] things-with-docs-map
        :when (t/has-bar? :poomoo.bars/docs thing)
        :let [doc (get-in thing [:bars :poomoo.bars/docs "doros-docs"])
              check-res (poomoo.doctest/check-examples (str doc \newline))]
        :when (seq check-res)]
  (println "Failed check.")
  (e/pprint thing)
  (e/pprint check-res))
```````

If all your examples are correct, it prints nothing. Not the most user-friendly
interface, but good enough for private checks. And don't puzzle over why we're
adding a newline; it just works around a shortcoming of the parser.


## Step 6: Putting it all in a JAR

First you have to define the Maven coordinates of the Datadoc JAR you want to
create and deploy. When you do this, you have to change `rmoehn` to your own
Clojars user name at least.

```clojure
(def coords {:group "org.clojars.rmoehn"
             :artifact "clojure-doro-docs"
             :version "1.7.0+001"
             :description
             (gr-utils/clean-up-string
               "clojure.core with added beginner documentation. Based on
               org.clojars.rmoehn:clojure:1.7.0+003:datadoc.")})
```

Then this will give you a JAR and a POM file in `target/datadoc`:

```clojure
(require '[grenada.exporters :as exporters])

(def things-with-docs (gr-converters/to-seq things-with-docs-map))

(exporters/jar things-with-docs
               "target/datadoc"
               coords)
```

Just for fun, you can read a Thing from the JAR you created:

```clojure
(-> (gr-sources/from-jar "target/datadoc/clojure-doro-docs-1.7.0+001-datadoc.jar")
    gr-converters/to-mapping
    (get-in [["org.clojure" "clojure" "1.7.0" "clj" "clojure.core" "concat"]
             :bars
             :poomoo.bars/docs
             "doros-docs"])
    e/pprint)
```


## Step 7: Deploying the JAR to Clojars

If you don't want to use
[lein-datadoc](https://github.com/clj-grenada/lein-datadoc), you can use an
ugly-but-working procedure from `grenada.postprocessors`:

```````clojure
(require '[grenada.postprocessors :as postprocessors])

(postprocessors/deploy-jar coords "target/datadoc" [; <Clojars user name>
                                                    ; <Clojars password>
                                                    ])
```````

That's it! If you want to know what else is available, have a look at the
[overview](doc/overview.md).
