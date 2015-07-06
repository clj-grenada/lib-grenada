# Ponderings

These are my incomplete thoughts on certain things and not the ultimate truth.
Please keep that in mind when my tone gets too ultimately truthy and I'm still
writing something wrong or forgetting something. It's just distracting to put
relativizing statements everywhere.

Ooh, and I'm not sure if this file will last. I just needed somewhere to drop my
thoughts on I'n'V.

## Identification and validation

 - When you `(deftag …)` a tag type `tt` with guten-tag, it defines a predicate
   `tt?`, which you can use to find out if some value is a `tt`.

 - According to guten-tag, something is a `tt` if it has the tag `:tt` and the
   entries fulfil the contract specified in the `(deftag …)` expression.

 - Thus, `tt?` performs identification and validation. This is not always
   desirable.

 - Suppose A is coming to work and wants to enter the building. He's got A's ID
   card and the biometrics are right, so the porter identifies him as A. (The
   answer to the question "A?" is True.) However, A forgot his sliderule and
   therefore cannot do his job X. (The answer to the question "valid? A for X"
   is False.) Because A cannot do his job X, he's not allowed to enter the
   building. A decides to cut down on his overtime account and cycles home again
   for today. There he meets his spouse who is late today. He/she identifies him
   by the usual means – ID card and biometrics (The answer to the question "A?"
   is still True.) – and makes him sweep the frontyard. A doesn't need anything
   special for this. (The answer to the question "valid? A for sweeping the
   yard" is True.)

 - We see that identification and validation should be separated:

    - Identification is finding out what something is according to certain
      criteria. (In many systems it makes sense that those criteria are the
      same everywhere, since we have validation separately. That's why above I
      chose ID card and biometrics for both instances of identification. There
      may be systems where different criteria are applied in different places,
      but that might be for different levels of trust, for example. It shouldn't
      be confused with criteria of fitness for some job.)

    - Validation is finding out whether something is fit for some job. In
      programming:

       - If something was made far away or out of our control, it should be
         verified explicitly according to documented rules. Those are also a
         guideline for the one who's making the thing.

       - If something was made close by in code that's under our control, we
         may validate it implicitly by employing certain techniques of defensive
         programming (fx safe-get) in order to catch silly mistakes. It would be
         a hindrance to have to explicate the rules for every piece of data
         we're dealing with.

 - Consequences:

    - Tagged values are nice. They combine easy identification (the only
      criteria being `tagged?` and that we have the right tag) and easy
      manipulation through the implemented interfaces.

    - The full validation as specified now in `grenada.things` is important for
      communication between different components from different authors.

    - However, fixed initial fields and built-in validation are limiting while
      we're inside a component. Inside a component it is often clumsy to adhere
      to the rules for component interoperability. But if we don't adhere to the
      rules, we can't enjoy the aforementioned niceties.

    - Therefore we have to abstain from using guten-tag's validation facilities
      and move all the limiting stuff from the constructors to separate
      validation functions.
