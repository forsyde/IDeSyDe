<!-- ---
layout: default
title: IDeSyDe - DSI and DSE
description: { { site.description } }
permalink: /dsi_dse
---

<script type="text/javascript" async src="http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML"></script> -->

# Design space identification and exploration

> **Note:** Math expressions may not render correctly on GitHub.com.
>
> GitHub's markdown rendering engine does not support the rendering of mathematical expressions using tools like MathJax. As a result, the math equations and symbols in this document may not display as intended.
>
> To ensure proper rendering of math expressions, we recommend that you generate the document locally using a markdown viewer or compiler that supports math rendering, such as Typora or VS Code with appropriate extensions. Alternatively, you can copy the markdown content and paste it into an online markdown editor that supports math rendering, such as StackEdit or Dillinger.
>
> Please refer to the generated output or use an alternative markdown viewer/compiler to view the math expressions accurately.

Design space identification (DSI) is a _compositional and systematic_ approach for creating design space exploration (DSE) solutions that distinguishes between the _design domain_ and the _optimisation and decision domain_. The design domain contains _design models_ typically used by MDE tools that implicitly define design spaces. The optimisation and decision domain contains _decision models_ that are sets of parameters and associated functions that explicitly abstract design spaces and are potentially explorable. The bridge between both domains is achieved through the combination of _composable identification rules_ and an _identification procedure_.

<!-- <figure id="fig:enhanced-position">

<figcaption>Enhanced overview of the <span data-acronym-label="dsi"
data-acronym-form="singular+short">dsi</span>-based <span
data-acronym-label="dse" data-acronym-form="singular+short">dse</span>
activity.</figcaption>
</figure> -->

Identification rules map design models and other decision models onto new decision models, i.e. _partially identify_ new decision models. The identification procedure is an automatic fix-point-based algorithm that incrementally produces decision models based on identification rules. Consequently, a DSE tool based on DSI does not require explicit dependencies between decision models but only the identification rules that partially identify them. For the same reason, backwards-compatibility after extensions is guaranteed by keeping identification rules existing before extension.

There are also _reverse identification rules_ and _bidding_. By reverse identification, we mean the activity of _reverse identifying_ explored design models out of the explored decision models resulting from exploration. We choose the word "reverse" instead of "inverse" as reverse identification rules are _not_ inverse mathematical functions of identification rules. By bidding, we mean the process where different explorers share exploration characteristics and compete to explore a decision model. The proposed enhancements are more convenient for implementation purposes as the explicit interfaces between identification, exploration and reverse identification can be implemented in general-purpose programming languages.

Therefore, the enhanced DSI approach <!-- (Figure [1](#fig:enhanced-position){reference-type="ref" reference="fig:enhanced-position"}) --> has three consecutive stages, each with its automated procedure. <!-- Concrete examples of DSI elements, such as identification rules and decision models, are given in the evaluation (Section [4](#sec:case-studies){reference-type="ref" reference="sec:case-studies"}). -->

## The identify-explore-reverse diagram<!--  {#sec:the-ier-diagram} -->

_Flow runs_ can be visualised through _identify-explore-reverse diagrams_. A flow run is the sequential execution of the three procedures to obtain explored design models out of input design models. Identify-explore-reverse diagrams show the interplay of design models, decision models, identification rules, reverse identification rules and explorers in a flow run. Here is an example diagram.

![An example identify-explore-reverse diagram.](./assets/images/svg/the_ier_diagram.svg)

We use this diagram as the guiding example to describe the conventions of identify-explore-reverse diagrams, as it fully captures such conventions. The first convention is that design models, decision models and explorers must be visually distinct. In the given figure, this convention is applied by choosing different colours and shapes. The second convention is that (reverse) identification rules label the arcs, showing which (reverse) identification rule identified the target design or decision models based on the source design or decision models. For instance, the identification rule $r_1$ may identify the decision model $X_1$ out of the design models $M_1$ and $M_2$; and the reverse identification rule $\rho_1$ may reverse identify the explored design models $M_1'$ and $M_2'$ out of the explored decision model $Y_1$. The third convention is that arcs from and to explorers describe biddings or exploration and must be visually distinct. In the example, the biddings are shown as label-less dashed arcs, whereas exploration is shown as label-less solid arcs.

We make two important remarks on these diagrams. First, identify-explore-reverse diagrams are comprehension aids, and DSI-based DSE tools do not need to generate them automatically when performing a flow run. XXxXxXx does not generate such diagrams, for instance. Second, (reverse) identification rules do not explicitly specify their input design and decision models stage [identification procedure](#first-stage-identification-procedure----secident-proc)<!-- {reference-type="ref" reference="sec:ident-proc"} and [3](#sec:implementation){reference-type="ref" reference="sec:implementation"}) -->; identify-explore-reverse diagrams shown where they _may succeed_ in partially identifying new models. This last observation is also valid for explorers in identify-explore-reverse diagrams.

## First stage: identification procedure <!-- {#sec:ident-proc} -->

In the first stage, a set of decision models $\mathcal{X}$ is identified from the input design models set $\mathcal{M}$ given a set of identification rules $R_{\mathit{ident}}$. In line with the beginning of this section, each identification rule $r \in R_{\mathit{ident}}$ is a mathematical function from the input design models set $\mathcal{M}$ and any set of decision models $\mathcal{X}_i$ to a new set of decision models $\mathcal{X}_{r,i+1}$: $\mathcal{X}_{r,i+1} = r(\mathcal{M}, \mathcal{X}_{i})$ where the additional subscript $r$ denotes that the decision models in the set $\mathcal{X}_{r,i+1}$ have been partially identified by $r$.

The set $\mathcal{X}_{i+1}$ is obtained by: $\mathcal{X}_{i+1} = \mathcal{X}_{i} \bigcup*{r \in R*{\mathit{ident}}} r(\mathcal{M}, \mathcal{X}_i)$ The requirement for every identification rule $r \in R_{\mathit{ident}}$ is that they are _monotonically increasing_ concerning the number of the _partially identified_ elements of $\mathcal{M}$. That is, an identification rule at step $i+1$ cannot partially identify _less_ elements than it did at step $i$ <!-- via [\[eq:ident-rule-def\]](#eq:ident-rule-def){reference-type="eqref" reference="eq:ident-rule-def"} -->. The monotonicity property of an identification rule is not restrictive; intuitively, it requires that if the partially identified part of the input models does not change, the identified decision model remains the same.

More precisely, consider $\mathit{elems}(M)$ to be a set representing
the elements of $M \in \mathcal{M}$, and $\mathit{elems}(\mathcal{M})$
the unions of such sets:
$\mathit{elems}(\mathcal{M})=\cup_{M \in \mathcal{M}}\mathit{elems}(M)$.
Let $\mathit{part}(X)$ be the set of partially identified elements of a
decision model $X \in \mathcal{X}_i$ with
$\mathit{part}(X) \subseteq \mathit{elems}(\mathcal{M})$; we also define
$\mathit{part}(\mathcal{X}_i) = \cup_{X \in \mathcal{X}_i}\mathit{part}(X)$
in analogy to the design models. Then, the monotonicity requirement is:
$
\mathit{part}(\mathcal{X}_{r,i}) \subseteq \mathit{part}(\mathcal{X}_{r,i+1}) \subseteq \mathit{elems}(\mathcal{M}) % \label{eq:monotone-req}
$

The identification procedure is performed until a fix-point
$\mathcal{X}$ is reached; that is, $\mathcal{X} = \mathcal{X}_{i+1}$
when
$\mathit{part}(\mathcal{X}_{i+1}) = \mathit{part}(\mathcal{X}_{i})$<!-- , as
outlined by
[\[eq:ident-rule-def\]](#eq:ident-rule-def){reference-type="eqref"
reference="eq:ident-rule-def"} and
[\[eq:ident-step-result\]](#eq:ident-step-result){reference-type="eqref"
reference="eq:ident-step-result"} -->. <!-- This fix-point exists due to the
monotonicity requirement
[\[eq:monotone-req\]](#eq:monotone-req){reference-type="eqref"
reference="eq:monotone-req"}, and the resulting identified decision
model set $\mathcal{X}$ is used in the next stage
[bidding procedure and exploration](#second-stage-bidding-procedure-and-exploration){reference-type="ref" reference="sec:dse-proc"}). -->

<!-- We remark that
[\[eq:ident-rule-def\]](#eq:ident-rule-def){reference-type="eqref"
reference="eq:ident-rule-def"} is different to the mathematical
definition in, as a set of
identified decision models are returned instead of a single decision
model. However,
[\[eq:ident-rule-def\]](#eq:ident-rule-def){reference-type="eqref"
reference="eq:ident-rule-def"} is conceptually equivalent to the one
definition of, since their
identification rules also progressively "partially identify more
elements of $\mathcal{M}$ by taking previously partially identified
$X \in \mathcal{X}$". -->

## Second stage: bidding procedure and exploration<!--  {#sec:dse-proc} -->

In the second stage, a set of explorers $\mathcal{E}$ bid for the
identified decision models and the winning bid proceeds to exploration.
Every explorer $E \in \mathcal{E}$ in our approach is based on a
decision and optimisation method. These methods may be generic decision
and optimisation frameworks<!--  such as [cp]{acronym-label="cp"
acronym-form="singular+short"} -->. Consequently, an explorer can be
incapable of solving a decision model if the decision model is not
compatible with the explorer's underlying method. Different explorers
may display different exploration characteristics when they can solve
the same decision model. For example, one explorer is the fastest to
find any feasible solution but the slowest to find the optimal solution.
In order to obtain the possible combinations between explorers and
decision models, the _bidding_ procedure is performed after the
identification procedure.

In the bidding procedure, every explorer $E \in \mathcal{E}$ bids for
each identified decision model $X \in \mathcal{X}$, returning whether
$E$ can explore $X$ _and_ the characteristics of performing this
exploration. A bid can _dominate_ another, in the sense that it
partially identifies more elements or has better exploration
characteristics. Therefore, the bidding procedure results in one or more
dominating bids for exploration
$\mathcal{B} \subset \mathcal{E} \times \mathcal{X}$; if there is more
than one, they are equally favourable in terms of exploration
characteristics.

More precisely, if there are up to $c$ exploration characteristics, the
bidding characteristics are $\mathit{bid}_E(X) \in \mathbb{R}^c$ for any
explorer $E \in \mathcal{E}$ and decision model $X \in \mathcal{X}$. If
$\mathit{bid}_E(X)$ is the zero vector in $\mathbb{R}^c$, then $E$
cannot explore the design space defined by the decision model $M$. For
every two distinct $(E, X)$ and $(E', X')$, we say that $(E, X)$
_dominates_ $(E', X')$ if:

1.  $\mathit{part}(X') \subset \mathit{part}(X)$ or,

2.  $\mathit{part}(X') = \mathit{part}(X)$ but all entries of
    $\mathit{bid}_E(X)$ are equal or greather than
    $\mathit{bid}_{E'}(X')$.

The resulting set $\mathcal{B}$ is computed via the dominance relation
by keeping only the dominant bids:

$$
\mathcal{B} =
\left\{
\begin{gathered}
(E, X) \in \mathcal{E} \times \mathcal{X}
\end{gathered}
\;\middle|\;
\begin{aligned}
\nexists & (E', X')\in \mathcal{E} \times \mathcal{X}, \\
&(E', X') \neq (E, X), \\
&(E, X) \text{ dominates } (E', X')
\end{aligned}
\right\}
$$

After bidding, a dominant bid is randomly chosen from $\mathcal{B}$ and
explored, returning a set of explored decision models. That is, a bid
$(E, X) \in \mathcal{B}$ is chosen and the exploration function of $E$,
$\mathit{explore}_E(X)$, returns a set $\mathcal{Y}$ of explored
decision models. The set $\mathcal{Y}$ is used for the last stage.

## Third stage: reverse identification procedure<!--  {#sec:reverse-ident-proc} -->

Despite their conceptual analogy, the reverse identification procedure
is simpler than the identification procedure. This is because the input
explored decision models $\mathcal{Y}$ are already dominant, which
entails that all the elements required to obtain the explored design
model are already present in $\mathcal{Y}$. Thus, one identification
step is enough and a fix-point-based procedure like
stage [identification procedure](#first-stage-identification-procedure----secident-proc)<!-- {reference-type="ref"
reference="sec:ident-proc"} --> is unnecessary. Each reverse identification
rule $\rho \in R_{\mathit{rev}}$ is mathematical function from the input
design models set $\mathcal{M}$ and the set of explored decision models
$\mathcal{Y}$ to a new set of explored design models
$\mathcal{M}_{\rho}'$:
$\mathcal{M}_{\rho}' = \rho(\mathcal{Y}, \mathcal{M})$ The final
explored design model set $\mathcal{M}'$ is given by the union of all
reverse identification rules results:
$$\mathcal{M}' = \bigcup_{\rho \in R_{\mathit{rev}}} \rho(\mathcal{Y}, \mathcal{M})$$
