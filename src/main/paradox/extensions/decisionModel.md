# Decision models

## Fundamentals

Let's start with how a @scaladoc[DecisionModel](idesyde.identification.DecisionModel) is defined
in the documentation:

@@@ note {title=Documentation}

A decision model holds information on how to build a design space that is explorable. In other
words, an object that implements this trait is assumed to provide parameters, and/or decision
variables, and/or analysis techniques for a certain design model. The trait itself is the bare
minimum so that the identification procedure can be performed to completion properly.

@@@

This tells us that besides the actual problem-specific parameters and methods that we want
to program, we need to add a handful "extras" for so that the identification procedure works properly.
In essence, these extras are defining what is the type of the elements ([`coveredElements`](/$scaladoc.base_url$/idesyde/identification/DecisionModel.html#ElementT-0)) and the type of
the element relationships ([`coveredElements`](/$scaladoc.base_url$/idesyde/identification/DecisionModel.html#ElementRelationT-0)) that this new @scaladoc[DecisionModel](idesyde.identification.DecisionModel)
is abstracting. Besides that, we need to provide how these custom types can be transformed to `String`s,
since that is the _de-facto_ common ground for unique identifiers that are also minimally understandable.
Finally and most importantly, we need to provide a set of [`coveredElements`](/$scaladoc.base_url$/idesyde/identification/DecisionModel.html#coveredElements-0) and a set of [`coveredElements`](/$scaladoc.base_url$/idesyde/identification/DecisionModel.html#coveredElementRelations-0).
This is fundamental because the identification procedure depends on this in order to: 1) terminate and converge to a unique value,
and 2) converge to the _correct_ value. Therefore, these choices are not to be taken lightly as they can mean
the difference between IDeSyDe working as expected and not.