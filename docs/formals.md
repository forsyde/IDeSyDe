---
layout: default
title: IDeSyDe - Formalities
description: { { site.description } }
permalink: /formals
usemathjax: true
---

# Important formal proofs

Here are proofs that ensure that IDeSyDe (or similar DSI tools) have as much correct behavior as possible;
it is also a place to give formal performance guarantees when possible.

## Bounds for non-exploration procedures

Make sure to read and understand [the key DSI concepts and definitions]({{ site.basurl }}/dsi_dse) before proceeding.

We discuss the asymptotic bounds for each non-exploration procedure
regarding "computational time". We present the bounds as theorems and
discuss further how they are directly bounded by the inputs of each
procedure.

__Theorem 1__. _For a set of explorers $$\mathcal{E}$$ and a set of
identified design models $$\mathcal{X}$$, let $$\kappa_{\mathcal{E}}$$ be
the worst complexity of evaluating the exploration properties for any
possible bid between $$\mathcal{E}$$ and $$\mathcal{X}$$. Then, the bidding
has complexity bound
$$\Theta(|\mathcal{E}||\mathcal{X}|\kappa_{\mathcal{E}})$$._

_Proof._
Since the computation of $$\mathcal{B}$$ requires the product composition between
explorers and decision models, The least amount of exploration
properties computed is $$|\mathcal{E}||\mathcal{X}|$$. Although each
estimation may have its own complexity, we know by definition that they
are upper bounded by $$\kappa_{\mathcal{E}}$$, proving that the bidding
procedure has the complexity bound
$$\mathcal{O}(|\mathcal{E}||\mathcal{X}|\kappa_{\mathcal{E}})$$.

However, since there is no early termination, as all products must be
computed for $$\mathcal{B}$$, the upper bound is also its lower bound,
proving the theorem. _QED_

__Theorem 2__. _For a set of design models $$\mathcal{M}$$, a set of
explored decision models $$\mathcal{Y}$$ and set of reverse identification
rules $$R_{\mathit{rev}}$$, let $$\kappa_{R_{\mathit{rev}}}$$ be the worst
complexity of evaluating any reverse identification rule in
$$R_{\mathit{rev}}$$ with $$\mathcal{M}$$ and $$\mathcal{Y}$$. Then, the
identification procedure has complexity bound
$$\Theta(|R_{\mathit{rev}}|\kappa_{R_{\mathit{rev}}})$$._

_Proof._ The reverse identification procedure requires only computing
$$\mathcal{M}_{\rho}'$$, which is determined by applying
_once_all $$r \in R_{\mathit{rev}}$$ to $$\mathcal{Y}$$. Therefore, the
amount of reverse identification rules evaluated is
$$|R_{\mathit{rev}}|$$. Since $$\kappa_{R_{\mathit{rev}}}$$ is the upper
bound of complexity for these rules by definition, we have that
$$\mathcal{M}_{\rho}' = \rho(\mathcal{Y}, \mathcal{M})$$ has the complexity bound
$$\mathcal{O}(|R_{\mathit{rev}}|\kappa_{R_{\mathit{rev}}})$$.

However, since there is no early termination, all reverse identification
rules must be evaluated; thus, the upper and lower bound are
equal, proving the theorem. _QED_

__Theorem 3__. _For a set of design models $$\mathcal{M}$$ and set of
identification rules $$R_{\mathit{ident}}$$, let
$$\kappa_{R_{\mathit{ident}}}$$ be the worst complexity of evaluating any
identification rule in $$R_{\mathit{ident}}$$ with $$\mathcal{M}$$ and any
set of decision models $$\mathcal{X}_i$$. Then, the identification
procedure has complexity bounds
$$\Omega(|R_{\mathit{ident}}|\kappa_{R_{\mathit{ident}}})$$ and
$$\mathcal{O}(|\mathit{elems}(\mathcal{M})||R_{\mathit{ident}}|\kappa_{R_{\mathit{ident}}})$$_

_Proof._ Without loss of generality, consider that the identification
procedure is at step $$i$$, with a set of identified decision models
$$\mathcal{X}_i$$. Computing $$\mathcal{X}_{i+1}$$ requires evaluating
$$|R_{\mathit{ident}}|$$ identification rules with $$\mathcal{M}$$ and
$$\mathcal{X}_i$$. Although every identification rule has its own
complexity, which might depend on both $$\mathcal{M}$$ and
$$\mathcal{X}_i$$, we know by definition that they are upper bounded by
$$\kappa_{R_{\mathit{ident}}}$$. Thus, the step from $$i$$ to $$i+1$$ has a
complexity of $$|R_{\mathit{ident}}|\kappa_{R_{\mathit{ident}}}$$.

After computation of $$\mathcal{X}_{i+1}$$, two conditions are possible:
either:

1. $$\mathit{part}(\mathcal{X}_{i+1}) = \mathit{part}(\mathcal{X}_{i})$$ or
2. $$\mathit{part}(\mathcal{X}_{i+1}) \neq \mathit{part}(\mathcal{X}_{i})$$.

If $$\mathit{part}(\mathcal{X}_{i+1}) = \mathit{part}(\mathcal{X}_{i})$$,
then the identification procedure has arrived at a fix-point and
terminates. If $$i$$ is the first step of the identification procedure, it
has been completed with one step, which proves that the complexity lower
bound is $$|R_{\mathit{ident}}|\kappa_{R_{\mathit{ident}}}$$.

Otherwise, if
$$\mathit{part}(\mathcal{X}_{i+1}) \neq \mathit{part}(\mathcal{X}_{i})$$,
there is at least one element $$x \in \mathit{part}(\mathcal{X}_{i+1})$$
which $$x \notin \mathit{part}(\mathcal{X}_{i})$$. By the monotonicity of
identification rules, we know that
$$x \in \mathit{elems}(\mathcal{M})$$ by definition. The second condition
can happen at most $$|\mathit{elems}(\mathcal{M})|$$ times during the
procedure; otherwise, we would need that
$$x \in \mathit{part}(\mathcal{X}_{i+1})$$ and
$$x \notin \mathit{elems}(\mathcal{M})$$, which is impossible. Therefore,
the complexity of the identification procedure is upper bounded by
$$|\mathit{elems}(\mathcal{M})||R_{\mathit{ident}}|\kappa_{R_{\mathit{ident}}}$$. _QED_

The three theorems explicitly show the complexity bounds for each
procedure but also introduce a constant $$\kappa$$ for each. These were
necessary analytical devices to carry out the proofs, but they may be
refined further within the DSI scope.

First, exploration properties are _estimates_ of the actual
DSE activity; they
should _not_ be computationally expensive algorithms. In other words,
$$\kappa_{\mathcal{E}}$$ is ideally a number dominated by the initial
number of elements in the design models. Specifically, we argue that
correct estimation techniques used in the bidding procedures will have a
complexity
$$\kappa_{\mathcal{E}} = \mathcal{O}(|\mathit{elems}(\mathcal{M})|^a)$$
where $$a$$ is a fixed positive integer.

Second, because reverse
identification rules are conceptually producing explored design model
that enriches the input design models, we argue that correct reverse
identification rules cannot arbitrarily extrapolate the number of
initial elements. That is to say, like estimation techniques, reverse
identification rules are dominated by the initial number of elements in
the design models:
$$\kappa_{R_{\mathit{rev}}} = \mathcal{O}(|\mathit{elems}(\mathcal{M})|^b)$$
where b is a fixed positive integer.

The same logic applies to
identification rules: they should not be computationally expensive
algorithms; rather, they ideally _prepare_ the parameters and associated
functions for expensive algorithms. This does not forbid identification
rules akin to "pre-processing" as long as they are acceptably
inexpensive, i.e. polynomial in the number of elements of the input
models. Mathematically, this means that
$$\kappa_{R_{\mathit{ident}}} = \mathcal{O}(|\mathit{elems}(\mathcal{M})|^c)$$
where $$c$$ is a fixed positive integer.

After this discussion, we can enunciate three corollaries of the
presented bound theorems with the assumptions of the foregoing
discussion.

__Corollary 4__. _For a set of explorers $$\mathcal{E}$$ and a set of
identified design models $$\mathcal{X}$$, the bidding has complexity bound
$$\Theta(|\mathcal{E}||\mathcal{X}||\mathit{elems}(\mathcal{M})|^a)$$ with
$$a \in \mathbb{N}$$_.

__Corollary 5__. _For a set of design models $$\mathcal{M}$$, a set of
explored decision models $$\mathcal{Y}$$ and set of reverse identification
rules $$R_{\mathit{rev}}$$, the identification procedure has complexity
bound $$\Theta(|R_{\mathit{rev}}||\mathit{elems}(\mathcal{M})|^b)$$ with
$$b \in \mathbb{N}$$._

__Corollary 6__. _For a set of design models $$\mathcal{M}$$ and set of
identification rules $$R_{\mathit{ident}}$$, the identification procedure
has complexity bounds
$$\Omega(|R_{\mathit{ident}}||\mathit{elems}(\mathcal{M})|^c)$$ and
$$\mathcal{O}(|R_{\mathit{ident}}||\mathit{elems}(\mathcal{M})|^{c+1})$$
where $$c \in \mathbb{N}$$._

We note again that these corollaries are based on the DSI assumption that
the procedures are acceptably efficient. For instance, if an I-module is
added to a flow run to be composed with others which contain one
expensive identification rule, then
Corollary 6 no longer holds.

To motive the discussion in practice, all the identification rules
across the four modules in IDeSyDe have a $$c=2$$. This value originates
from the fact that few identification rules look for "neighboring"
patterns of elements in the design model, which might require quadratic
time in the number of elements. For the others, IDeSyDe has $$a=b=1$$,
since the reverse identification rules and exploration estimations are
rather direct, as DSI promotes.
