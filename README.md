# lib-grenada

A Clojure library that helps you doing stuff according to the [Grenada
spec](https://github.com/clj-grenada/grenada-spec). Currently this is in an
early stage of development and not supposed to be used by ordinary people.

## Documentation

### Docstrings

 - You'll find docstrings like this:

   ```clojure
   "

   Some text here."
   ```

   This format is intentional. Almost every definition should – in the end –
   have a primary description of what it is or does. This primary description
   stands at the beginning of the docstring. However, in the early stages of
   development I usually don't want to write a primary description, but have to
   write some other important thing: "Some text here." If I wrote this at the
   beginning of the docstring, I might forget that the definition to which it
   belongs is still lacking a primary description. That's why I don't write it
   at the beginning of the docstring.

### Comment annotations

 - I use comment annotations as defined in the [Clojure Style
   Guide](https://github.com/bbatsov/clojure-style-guide#comment-annotations).
   Additionally:

 - DE-HACK: Same definition as HACK in the style guide. Just more consistent
   with the other annotations, which are imperative verbs and not nouns.

 - REFACTOR: Something that I don't consider a HACK, but that should still be
   written in a different way or be put somewhere else.

 - MAYBE: A higher-order annotation indicating that some thought should be put
   into whether (and how) to carry out the respective action.

### Rationale

Rules for where to write down rationale:

 1. If it is important for people looking at the code or wanting to change it,
    write it as a comment.

 2. If the reason for a particular change is not immediately apparent, write it
    in the commit message.

(These rules weren't followed from the beginning.)

## Comments on the code

 - I try to mark everything that's not part of the public API as private.

 - Quite often you will find some helper functions at the top of namespace A
   that are universally useful, but at the time of development only used in A.
   If it turns out that namespace B also wants to use them, you should put them
   into a namespace U. They are marked as private in order to prevent people
   from directly using them out of A.

### Terminology

 - I call what is defined with (defn …) or returned by (fn …) a
   "function" only when it is indeed referentially transparent (maybe bar
   exceptions and the like). What we colloquially call "functions" are functions
   only in some messed up, functional programming-ignorant sense. In general I
   use "procedure" (SICP/Scheme lingo) or "fn".

### Common names and abbreviations

 - prefixes "a", "the": I put them in order to avoid shadowing `clojure.core`
   bindings. For example, `avar`, `thevec`.
 - k: key (maybe also used for keyword, but I'll try and avoid this in the
           future)
 - kw: keyword
 - m: map
 - nm: name
 - o: object
 - res: result
 - sth: something
 - v: vector, value

### Extension metadata

 - Occasionally you will find Grenada extension metadata attached to some
   things. These are proof-of-concept and are not yet backed by actual Grenada
   extensions.

### Other conventions

 - Clojure lookup semantics (nil returns when something isn't there) have
   tripped me too often. I will mostly abstain from using things like `(a-map
   :a-key)` or `(:a-key a-map)` or `(a-vec 42)`. Instead I will use
   `plumbing.core/safe-get` when I expect a key to be present and
   `clojure.core/get` when I allow the key not to be present. `clojure.core/get`
   instead of the short syntax in order to make this explicit. Only when I have
   verified beforehand that an entry exists might I use the short syntax.

## Version Control

 - Branching and merging workflow follows [Driessen's
   model](http://nvie.com/posts/a-successful-git-branching-model/).
 - I reserve the right to rewrite history on and force-push to my own feature
   branches. I've warned you.

## License

See [LICENSE.txt](LICENSE.txt).
