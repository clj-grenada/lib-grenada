# The case of Dorothy the Documenter

(If you've found this part of the documentation by chance, but aren't sure
whether it is what you're looking for, go to the [overview](overview.md).)

If you've read the Clojure Gazette interview about lib-grenada during the Google
Summer of Code, you already know Dorothy the Documenter and her quest. This here
is a tutorial that guides you through the steps she would take. If you don't
know Dorothy the Documenter: in this tutorial I'll show you how to build a
Datadoc JAR with beginner documentation for the fns and variables in
`clojure.core`.

## Step 1: Get a Datadoc JAR and look what's in there

A Datadoc JAR is a JAR file with data documenting some Clojure code. To start
off, create a Leiningen project and add the newest lib-grenada to the dependency
list in project.clj. Change the Clojure version to 1.7.0. Then start your
favourite environment for evaluating Clojure code (probably some sort of REPL).

Evaluate the following. It will download a Datadoc JAR with documentation for
the core Clojure namespaces and read in the data.

```clojure
(require '[grenada.sources :as gr-sources])

(def data (gr-sources/from-depspec '[org.clojars.rmoehn/clojure "1.7.0+001"
                                     :classifier "datadoc"]))
```

You see that Datadoc JARs are specified in the same way as Leiningen
dependencies, which in turn are based on Maven coordinates. Now have a look
at the data.

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

          ```
          ["org.clojure" "clojure" "1.7.0" "clj" "core.core" "map"]

          Arg lists: …

          …
          ```
        - We can write a short snippet to generate such files for all Finds in
          clojure.core.
        - Can also write documentation for a namespace:
          ```
          ["org.clojure" "clojure" "1.7.0" "clj" "core.core"]
          …
          ```
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
