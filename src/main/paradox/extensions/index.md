@@@ index

* [Decision models](decisionModel.md)
* [Design models](designModel.md)

@@@


# Extending IDeSyDe

Since IDeSyDe is built on top of the [Design Space Idenfitication](https://ieeexplore.ieee.org/document/9474082) (DSI) approach,
there are two major entry points for extension in the tool: extending the abstractions that can be solved and extending
the [Model-Driven Engineering (MDE)](https://www.sciencedirect.com/topics/computer-science/model-driven-engineering) frameworks
supported. We note that extending support for another tool does not strictly require this tool to follow any MDE method or use its
framework. Rather, the only necessary thing is that it can fit the DSI idea of a "Design Model".

On more concrete terms, if you want to add a new solvable abstraction (think in terms of equations, constraints and their parameters etc),
then you want to create one or more @scaladoc[DecisionModel](idesyde.identification.DecisionModel)s 
as outlined @ref[here](decisionModel.md).
If you want to add a bridge from IDeSyDe and the problems it can solve to another tool or tool-suite, you want to create a new @scaladoc[DesignModel](idesyde.identification.DesignModel) as outlined @ref[here](designModel.md).

In either case, you'll most likely need to create one or more identification and integration rules and bundle them in
a @scaladoc[IdentificationModule](idesyde.identification.IdentificationModule).