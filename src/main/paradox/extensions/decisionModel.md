# Decision models

## Fundamentals

Let's start with how a @scaladoc[DecisionModel](idesyde.identification.DecisionModel) is defined
in the documentation:

@@@ note { title=Documentation }

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

However, this discussion also suggests a good starting point for an abstracted element: if they have to be
stringifiable to an unique `String` identifier, why not make the elements of the @scaladoc[DecisionModel](idesyde.identification.DecisionModel)
`String`s themselves? That's exactly the path taken with @scaladoc[StandardDecisionModel](idesyde.identification.common.StandardDecisionModel).
We could still go on with a very custom element type (`ElementT`), but them we have to guarantee that every distinct element
in the model will always generate a unique identifier. 

## The standard decision model

Building on top of @scaladoc[StandardDecisionModel](idesyde.identification.common.StandardDecisionModel) adds a new dependency to our 
new extensions: the `scala-common` module. Some might think that adding dependencies is always risky, but this one particular dependency
is *highly recomended*. The cde in this module
 can greatly simplify life with IDeSyDe, since most of the "vendor-agnostic" stuff is in this module, including
the aforementioned @scaladoc[StandardDecisionModel](idesyde.identification.common.StandardDecisionModel), but also other
children decision models encoding known design scenarios from the scientific and engineering literature.

Moving on, now the only required to implement the trait @scaladoc[StandardDecisionModel](idesyde.identification.common.StandardDecisionModel)
is to return a set of strings and set of pairs of strings.

For example, let's take the decision model @scaladoc[SharedMemoryMultiCore](idesyde.identification.common.models.platform.SharedMemoryMultiCore) from `scala-common` , which abstracts a shared-memory multi-core hardware architecture. Although there are
a handful of parameters that describe many performance characteristics of the architecture, the covered elements are fewer; namely,
they are the processing elements, the memory elements and the communication elements:

@@snip [SharedMemoryMultiCore.scala](/scala-common/src/main/scala/idesyde/identification/common/models/platform/SharedMemoryMultiCore.scala) { #covering_documentation_example }

where the elements being aggregated are simply lists of strings. 

## A step-by-step example

Now that we know to use @scaladoc[StandardDecisionModel](idesyde.identification.common.StandardDecisionModel), let's do a step-by-step construction of a new decision model. In this tutorial, we shall 
create the parameters that abstract nicely [Synchronous Dataflow (graphs)](https://ieeexplore.ieee.org/document/1458143) or SDF(Gs), for short.

Since SDFs are essentially labelled directed graphs, we can start with the graph part. Every directed graph must have a set of nodes and
a set of arcs, which are called actors and channels in SDF terminology. Thus, we could start writing the decision model class like:

```
final case class SDFApplication(
    val actors: Vector[String],
    val channelsSrcs: Vector[String],
    val channelsDsts: Vector[String],
) extends StandardDecisionModel {

    val coveredElements = actors.toSet
    val coveredElementRelations = channelsSrcs.zip(channelDsts).toSet
    def uniqueIdentifier = "SDFApplication"

}
```

Although this captures the graph part, now we are missing the "labelled" part of it: the data rates. Since SDFs have
fixed production and consumption rates, and they always exist for every channels, we could add to the decision simply via:

```
final case class SDFApplication(
    val actors: Vector[String],
    val channelsSrcs: Vector[String],
    val channelsDsts: Vector[String],
    val production: Vector[Int],
    val consumption: Vector[Int],
) extends StandardDecisionModel {

    val coveredElements = actors.toSet
    val coveredElementRelations = channelsSrcs.zip(channelDsts).toSet
    def uniqueIdentifier = "SDFApplication"

}
```

which would take care of it. Note that the covered elements have not changed! This does not mean that the decision model created
is wrong, because we added information that is conceptually identified from the same elements and their relations. That is, we "know"
from the model definition that the production and consumption rates are part of the channel definition, and therefore it is not
a problem that new information was added to the decision model _while_ the covered elements and relations remained the same.
A final key elements we are missing is the initial tokens present in each channel. Once more, since we were supposed to start with
this information, it makes sense that no new element is being covered by this decision model extension.

```
final case class SDFApplication(
    val actors: Vector[String],
    val channelsSrcs: Vector[String],
    val channelsDsts: Vector[String],
    val production: Vector[Int],
    val consumption: Vector[Int],
    val numInitialTokens: Vector[Int]
) extends StandardDecisionModel {

    val coveredElements = actors.toSet
    val coveredElementRelations = channelsSrcs.zip(channelDsts).toSet
    def uniqueIdentifier = "SDFApplication"

}
```

And there we have it! The most basic decision model representing SDFs, without taking into consideration execution times, actors sizes etc.
One could checkout the actual decision model for SDFs in `scala-model`: @scaladoc[SDFApplication](idesyde.identification.common.models.sdf.SDFApplication) and see differences. This is because the SDF decision model in the common module also takes into consideration the practical
factors briefly mentioned in order to later perform design space exploration on top of this decision model.

That's it! This is how one would create a correcy decision model, @scaladoc[StandardDecisionModel](idesyde.identification.common.StandardDecisionModel) specifically, so that it is a expected for the identification procedure. From here, it would now be
necessary to define @ref[identification rules](identRules.md) to actually put this decision model to some use.