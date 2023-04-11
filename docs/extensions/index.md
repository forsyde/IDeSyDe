---
layout: default
title: IDeSyDe - Extensions
permalink: /extensions
---


# Extending IDeSyDe

Since IDeSyDe is built on top of the [Design Space Idenfitication](https://ieeexplore.ieee.org/document/9474082) (DSI) approach,
there are two major entry points for extension in the tool: extending the abstractions that can be solved and extending
the [Model-Driven Engineering (MDE)](https://www.sciencedirect.com/topics/computer-science/model-driven-engineering) frameworks
supported. We note that extending support for another tool does not strictly require this tool to follow any MDE method or use its
framework. Rather, the only necessary thing is that it can fit the DSI idea of a "Design Model". Here's a checklist of up to four
points that might need to be extended to support your particular problem:

1. If you have tool/tool-suite that is not yet bridged with IDeSyDe, you most likely need to you create a new [DesignModel](idesyde.identification.DesignModel) as outlined [here](designModel),
2. If you have a problem description (think in terms of equations, constraints and their parameters etc) that is not yet in any module within 
   IDeSyDe, then you need to create one or more [DecisionModel](idesyde.identification.DecisionModel)s 
   as outlined [here](decisionModel).
3. For some [DecisionModel](idesyde.identification.DecisionModel)s, you might need to create 
   new [Explorer](idesyde.exploration.Explorer)s in order to actually solve these decision models, i.e.
   explore the design space they define.
4. For every new [DecisionModel](idesyde.identification.DecisionModel), you'll likely need to create new identification rules
   to identify this decision model and connect to the ones already available.
5. For every new [DesignModel](idesyde.identification.DesignModel), you'll likely need to create new identification rules
   _and_ integration rules to identify decision models on top of this design model _and_ retrieve the results back into a format
   the original [DesignModel](idesyde.identification.DesignModel) can consume.
6. For every new identification or integration rule created, you need to register it into an existing 
   [IdentificationModule](idesyde.identification.IdentificationModule) in the IDeSyDe modules or create a new one.

Then, you are ready to ude IDeSyDe exactly in your situation. All six steps are necessary only if you are starting something from
the very fundamentals, i.e. a new tool suite with a new approach to a new problem with a new algorithm to solve it. With time,
as the open-source and common modules of IDeSyDe grow, less and less of these steps will be necessary for every extension.