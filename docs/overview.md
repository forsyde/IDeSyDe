---
layout: default
title: IDeSyDe - Overview
description: { { site.description } }
permalink: /overview
usemathjax: true
---

# IDeSyDe overview {#sec:implementation}

This documentation section is reasonably self-contained, but uses many concepts, terms and symbols from [the DSI section]({{ site.baseurl }}/dsi_dse).

IDeSyDe follows a __multi-module architecture__
as shown in the figure below, where an __orchestrator__
coordinates different modules during a [flow run]({{ site.baseurl }}/dsi_dse#design-space-identification-and-exploration). The modules are loosely coupled
standalone executable programs and can be either __identification
modules__ (I-modules) or __exploration modules__ (E-modules). For the sake
of simplicity, we exclusively use the word __modules__ to imply both
I-modules and E-modules. During a flow run, this loose coupling is
realised through a shared __workspace__ between the orchestrator and the
modules.

<img src="{{ site.baseurl }}/assets/images/svg/idesyde_architecture_mm.svg" style="width: 100%;" alt="Multi-module architecture diagram">

This architecture enables seamlessly combining independently developed
modules. The orchestrator can detect all modules available in the
workspace dynamically; thus, a problematic module can be updated or
replaced in isolation from the other modules or the orchestrator. More
importantly, this architecture enables direct use of
model-driven-engineering (MDE) libraries and
code in their target language to decrease the implementation effort. For
example, a Java-based I-module may directly use [ECore-based](https://wiki.eclipse.org/Ecore) models as
design models. Decoupled interaction between independently developed
modules requires programming-language-specific techniques without this
modular architecture.

## Workspace, model headers and core libraries {#sec:workspace-header-corelib}

IDeSyDe uses the operating system's filesystem with naming conventions
as the shared workspace. Namely, a __run path__ is given to IDeSyDe at the
beginning of every flow run; IDeSyDe creates one nested folder in this
run path for the sets $$\mathcal{M}$$, $$\mathcal{X}$$, $$\mathcal{Y}$$ and
$$\mathcal{X}'$$, per [the design space identification (DSI) approach]({{ site.baseurl }}/dsi_dse).
The orchestrator creates all four appropriate filesystem folders.
Then, modules produce and consume model data and __headers__ in these folders during a flow run.

A design or decision model __header__ is an interchangeable __data record__
with three entries:

1. a UTF8 identifier for the model category, e.g. `SDF`,

2. a UTF8 encoded path to model data that may be empty,

3. a UTF8 string list that represent $$\mathit{elems}$$ or
    $$\mathit{part}$$.

Headers in IDeSyDe capture $$\mathit{elems}$$ or $$\mathit{part}$$ in the
universe of Strings, parallel to the model data. Consequently, the orchestrator and
modules can exchange identification information in a decoupled and
efficient manner. By efficient, we mean by exchanging only mandatory
information compactly.

We remark that the notion of elements is more general than that of nodes
in graphs. IDeSyDe, for instance, encodes in $$\mathit{elems}$$ and
$$\mathit{part}$$ both nodes and arcs of graph-like models.

IDeSyDe provides __core libraries__ incorporating the previous discussion
to facilitate its extension on a target programming language.
Specifically, a core library for a programming language is the set of
routines, data structures and interfaces that facilitates correctly
extending IDeSyDe. For instance,
the snippet below shows the decision model header
definition in the Scala core library. Developing a core library is a
one-time engineering effort for each language. Currently, IDeSyDe
provides core libraries in Rust and Scala.

<figure id="alg:header-example">
<div class="sourceCode" id="cb1" data-language="Scala"><pre
class="sourceCode scala"><code class="sourceCode scala"><span id="cb1-1"><a href="#cb1-1" aria-hidden="true" tabindex="-1"></a><span class="cf">case</span> <span class="kw">class</span> <span class="fu">DecisionModelHeader</span><span class="op">(</span></span>
<span id="cb1-2"><a href="#cb1-2" aria-hidden="true" tabindex="-1"></a>    <span class="kw">val</span> category<span class="op">:</span> <span class="ex">String</span><span class="op">,</span></span>
<span id="cb1-3"><a href="#cb1-3" aria-hidden="true" tabindex="-1"></a>    <span class="kw">val</span> body_path<span class="op">:</span> <span class="ex">Option</span><span class="op">[</span><span class="ex">String</span><span class="op">],</span></span>
<span id="cb1-4"><a href="#cb1-4" aria-hidden="true" tabindex="-1"></a>    <span class="kw">val</span> covered_elements<span class="op">:</span> <span class="ex">Set</span><span class="op">[</span><span class="ex">String</span><span class="op">]</span></span>
<span id="cb1-5"><a href="#cb1-5" aria-hidden="true" tabindex="-1"></a><span class="op">)</span> <span class="op">{</span> <span class="co">/__...__/</span> <span class="op">}</span></span></code></pre></div>
<figcaption>Scala decision model header from the Scala core
library.</figcaption>
</figure>

Decision models that are exchanged between modules implemented in
different programming languages cannot have associated functions. This
potential limitation is due to the lack of a standard format to share
__arbitrary functionality__ between programs developed in different
languages. However, for every decision model with associated functions,
a new identification rule can be created that produces a new decision
model without associated functions; this new decision model aggregates
the fixed parameters of the original decision model with the result of
its associated functions. Therefore, in this case, the lack of
associated functions is not a limitation but a one-time engineering
effort overhead. On the other hand, modules implemented in the same
programming language can exchange decision models with associated
functions via __common libraries__ on top of the core libraries.

## Modules, orchestrator and blueprints {#sec:blueprints-libraries}

The connection between the DSI definitions and the modules is as follows.
Consider that each I-module $$i$$ has the identification rules set
$$R_{\mathit{ident}, i}$$ and the reverse identification rules set $$R_{\mathit{rev}, i}$$.
Their aggregated behaviour corresponds to $$R_{\mathit{ident}}$$ via
$$R_{\mathit{ident}} = \cup_{i} R_{\mathit{ident}, i}$$ and the
$$R_{\mathit{rev}}$$ via $$R_{\mathit{rev}} = \cup_{i} R_{\mathit{rev}, i}$$. This logic applies
analogously to E-modules, where all E-module $$j$$ explorers
$$\mathcal{E}_j$$ aggregate to $$\mathcal{E}$$ via $$\mathcal{E} = \cup_j \mathcal{E}_j$$.

In practice, this aggregation is achieved via the orchestrator.
The orchestrator is a standalone program built on top of a chosen core
library that keeps track of the workspace and requests actions from
modules within it. For instance, the orchestrator requests all I-modules
to perform an identification step with the inputs and identified models
during the identification procedure. After the I-modules have finished a
step, the orchestrator checks the headers for a fix-point, repeating
another step if necessary. The orchestrator is a one-time engineering
effort, and IDeSyDe's orchestrator is implemented in Rust.

Like core libraries, IDeSyDe provides __module blueprints__ for I-modules
and E-modules; these blueprints build on top of core libraries. A module
blueprint for a programming language is an additional set of routines,
data structures and interfaces that enables the rapid creation of
I-modules and E-modules as command-line applications. Creating new
modules through the blueprints guarantees that the orchestrator uses the
created modules reliably on the appropriate procedures. Developing
module blueprints is a one-time engineering effort.

## Performance aspects {#sec:impl-performance}

The high decoupling of the multi-modular architecture and IDeSyDe,
potentially slows down all non-exploration steps in a flow run. The
slower performance arises from two different overhead sources. First,
the overhead involved in exchanging decision models via the shared
workspace instead of in memory. Second, the overhead of repeatedly
spawning and executing I-modules and E-modules processes. The latter
factor is the dominant overhead and includes, aside from operating
systems overheads, warm-up times of modules implemented in just-in-time
compiled or interpreted languages; examples are Java-based or
Python-based modules. A portion of these overheads can be mitigated if
the modules are long-lived services instead of short-lived processes as
in the current IDeSyDe version, at the cost of increased orchestration
effort. We leave this architectural direction for future work.

Fortunately, the slow-down is __bounded__. This is because the
identification, bidding, and reverse identification procedures terminate
in a __polynomial number of steps__. Therefore, the mentioned overheads
increase the duration of the procedures by a bounded amount. Moreover,
for non-trivial design spaces, i.e. input models, the exploration
procedure __asymptotically dominates__ other non-exploration procedures with
worst-case exponential complexity.
