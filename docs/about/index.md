title: IDeSyDe - About
layout: default
top-navbar: true
permalink: about.html
---

# About

IDeSyDe is a *generic* tool [for the Design Space Exploration activity](https://forsyde.github.io#our-vision)
within a [Model-Driven Engineering (MDE)](https://www.sciencedirect.com/topics/computer-science/model-driven-engineering)
Process such as [ForSyDe](https://forsyde.github.io/). In particular,
the tool primarily targets *embedded systems* MDE processes, but is not limited to them.


The key underlying concept of IDeSyDe is [Design Space Idenfitication](https://ieeexplore.ieee.org/document/9474082).
This concept give a foundation for separating mathematical formulations and algorithms commonly involved in synthesis and analysis within MDE processes
from the system models it operates on, aside from defining a systematic procedure for extension.
Therefore, to the MDE user, IDeSyDe operates on their *system models*, e.g. [AMALTHEA](https://www.eclipse.org/app4mc/) or 
[ForSyDe IO]({{ project.forsydeio }}).

<!-- IDeSyDe is in fact an implementation of it, and it powers all guarantees and decoupling
seen in the tool itself.  Try [Concepts](/concepts)! -->

<!-- ## What is IDeSyDe? -->

As such, IDeSyDe is not a mathematical solver such as [Gurobi](https://www.gurobi.com/) or 
[CPLEX](https://www.ibm.com/analytics/cplex-optimizer), it is not a constraint solver
such as [Gecode](https://www.gecode.org/) or [chuffled](https://github.com/chuffed/chuffed),
nor a general meta-heuristic optimization framework
such as [Opt4j](https://sdarg.github.io/opt4j/) or [JMetal](https://jmetal.github.io/jMetal/).
It uses all these applications and libraries to enrich models that make
better sense to a system designer, such as the mentioned [AMALTHEA](https://www.eclipse.org/app4mc/) or 
[ForSyDe IO]({{ project.forsydeio }}). 
