---
layout: default
title: Index
nav_order: 1
---

Welcome to IDeSyDe's documentation page! Here you can find some quick links to help you navigate the documentation
and also to situate you about the tool (and its methods).

# Quick facts

## What is IDeSyDe?

A *generic* tool [for the Design Space Exploration activity]({{ site.project.forsyde }}#our-vision)
within a Model-Based System design flow such as [ForSyDe]({{ site.projects.forsyde }}).

The key element in IDeSyDe is the underlying concept of **Design Space Idenfitication**.

IDeSyDe is in fact an implementation of it, and it powers all guarantees and decoupling
seen in the tool itself. Want to know more? Try [Concepts]({{ site.baseurl }}/concepts)!

## What is IDeSyDe not?

It is not a mathematical solver such as [Gurobi](https://www.gurobi.com/) or 
[CPLEX](https://www.ibm.com/analytics/cplex-optimizer), it is not a constraint solver
such as [Gecode](https://www.gecode.org/) or [chuffled](https://github.com/chuffed/chuffed),
nor a general meta-heuristic optimization framework
such as [Opt4j](https://sdarg.github.io/opt4j/) or [JMetal](https://jmetal.github.io/jMetal/).

It uses all these applications and libraries to explore the design space of models that make
better sense to a system designer, such as [AMALTHEA](https://www.eclipse.org/app4mc/) or 
[ForSyDe IO]({{ site.projects.forsydeio }}). 

A quick glance at [Concepts]({{ site.baseurl }}/concepts) can likely clarify this further!

## What can IDeSyDe do for me?

As a tool for Design Space Exploration, IDeSyDe can potentially give you design decisions on your models
such as mappings, schedules, allocations among others. 

These decisions are not random: they respect
design constraints such as memory, time or energy.

This also means that if you let IDeSyDe run long enough, and have just the right amount of computational resources,
it can can give back a solution "*Your design is not possible*".
It is always good to know designs are not possible before they crash!


## How can I use it?

That depends on how big the current identification library is!
Check out [Installation]({{ site.baseurl }}/installation) for instructions on how to install IDeSyDe
in your machine.
