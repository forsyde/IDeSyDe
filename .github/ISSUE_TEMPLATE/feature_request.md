---
name: Feature request
about: Suggest a novel or improvement idea for IDeSyDe
title: "[FEATURE REQUEST]"
labels: enhancement
assignees: ''

---

**Please describe your feature request tkaing into consideration IDeSyDe's envisioned extension points**
IDeSyDe envisions three major extension points:
 1. Adding support for new "input file formats", or "model formats". These are the `DesignModel`s pervading the codebase. If you have your own MDE descriptions, this is likely where you want to start.
 2. Adding a new re-usable intermediate abstraction. These are the `DecisionModel`'s pervading the codebase. You want to add a new `DecisionModel` if you think you have a good model abstracting many existing formats. For example, the `AperiodicAsynchronousDataflow` decision model captures dataflow applications with a certain regularity from **any** input format, i.e. design model.
 3. Adding a new exploration algorithm. These are the `Explorer`'s in the codebase. You want this if you have a new algorithm or "solvable" problem mathematical description for one of the decision models that exist or you created.

Naturally, you might want some other feature that is not present in these 3 cases. Like the improvement of something that IDeSyDe is already doing, or to output internal numbers that IDeSyDe already computes internally (there are many). This is also fine! Just make sure to describe it nicely.

**Describe the solution you'd like**
A clear and concise description of what you want to happen.

**Describe alternatives you've considered**
A clear and concise description of any alternative solutions or features you've considered, specially in light of the IDeSyDe's extension points.
