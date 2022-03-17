---
layout: default
title: Design Space Identification
permalink: /concept/dsi
nav_order: 2
parent: Concepts
---

# Design Space Identification (DSI)

## Motivation

Design Space Identification is an approach to overcome the coupling problem between
models that a designer understands and likes, and models that a computer understands and solves for
Design Space Exploration (DSE).

Although this distinction is not black and white but rather a spectrum, 
there are a couple models commonly found in (embedded) system design that illustrates well
the conceptual difference,

1. UML, SysML, Simulink models are "designer" models: They have significance to the designer
   and potentially are objects one like to implement.
2. Constraints Satisfaction Programs, Mixed-Integer Linear programs, Genetic algorithms are "computer" models:
   They tend to represent the intent to map/schedule/allocate pieces of the system together, as in
   scheduling software in the hardware.

Getting from 1 to 2 is most of the time done via expertise: defining equations, defininig genomes and crossover
operators, defining search procedures etc, that conform to a smart human intuiton on how to solve the original
DSE problem at the designer model level. 
Other times, a *meta-DSE* framework is defined, which requires the designer to represent one's system model
in a framework-predefined fashion if one whishes to benefit from DSE. It also follows convetionally
that the computer models produced are in predefined categories, say, job scheduling genetic algorithms.

Naturally, we would love to use such _meta-DSE_ frameworks without it invading our design models and maybe
having the ability to switch between the computer, or decision, models generated for them.

This is where DSI comes in.

**That is, DSI is a systemic and composable approach to flexiable and efficient meta-DSE.**

## Overview

To create this systemic and composable path from designer models to decision models, we focus in the steps.
Yes, DSI is just about formalizing and composing transformative steps in the models.

Consider the following example:

1. Suppose that we "pattern match" the model and _identify_ that we Time Triggered Platform in the design model.
2. Then, suppose that we "pattern match" again (or at the same time) and _identify_ that we have a SDF application in the design model.
3. We look at the _identified_ models and notice we can unify the SDF apps decision model to the TT platform decision model 
   to create a set of equations that can be solved by Gurobi or CPLEX.
4. We look at them agian (maybe at the same time) and identify that we can relay the SDF apps to the TT platform as an input to
   the SDF3 toolsuite.

These 4 steps represent faithfully the core ideas behind DSI, of executing composable steps, starting from the design model and
building up until "explorable" decision models are made. Then, if we have the explorers for them, we can do a full DSE as if we
programmed them by hand!

**This enable the solution strategies to become one-time engineering efforts**.

## Implementation in IDeSyDe

As a sort of academic *model compiler*, it is hard to maintain a tool which is nearly a product, so we have to compromise.
This compromise in IDeSyDe is that almost all explorers are external and the tools focuses only in doing the introduced
**Design Space Identification** efficiently. Like this:

![]({{ site.baseurl }}/assets/images/svg/idesyde-flow.svg)

The AMALTHEA reference is used to symbolize that the tool potentially interfaces with other tools for now
by model equivalences on ForSyDe IO, though the only model it currently accepts as input and ouputs is ForSyDe IO.