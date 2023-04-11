---
layout: default
title: IDeSyDe - Usage
permalink: /usage
---


# Usage

Short answer: [Install](usage/install.md) IDeSyDe and follow the [Quick start](usage/quickstart.md) to get an intuitive grasp!

Long answer:
IDeSyDe being a proof-of-concept prototype, there were decisions that simplified the implementation in order
to prove the concepts quickly. The first one being that the tool that is distributed only accepts [ForSyDe IO]({{ project.forsydeio }})
models (this is not a limtation as [ForSyDe IO]({{ project.forsydeio }}) connects to other MDE frameworks). 
Second, only a handful of MDE "scenarios" (i.e. design spaces) are implemented and distributed with the tool.
They can be found in [Supported DSE](status.md).
Therefore, direct use of the tool depends on the design spaces distributed with the tool itself. 