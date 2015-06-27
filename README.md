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

### Common abbreviations

 - nm: name – Can't write it out, because Clojure already has such a function.

### Extension metadata

 - Occasionally you will find Grenada extension metadata attached to some
   things. These are proof-of-concept and are not yet backed by actual Grenada
   extensions.

## License

See [LICENSE.txt](LICENSE.txt).
