<!-- ---
layout: default
title: Guidelines
nav_order: 3
permalink: /usage/guidelines
parent: Usage
--- -->

# Guidelines

like other tool(chain)s in the market, IDeSyDe is no silver bullet.

Here's a quick decision flow chart regarding IDeSyDe usage:

![](/assets/images/svg/idesyde-usage-flow.svg)

Since the tool takes a model that resembles an engineer's perspective of an embedded system,
and not a set of equations, there are basically two questions to be answered by you:

1. Are your system models reliably described by the model covered in [ForSyDe IO]({{ site.projects.forsydeio }})?
2. Are there [identification rules and models](/usage/support) for your system models in IDeSyDe?

If you answered _yes_ to both of these, all you need to do is write down the ForSyDe IO model
and use IDeSyDe on it! The time to solution can be a bit dauting, but it the search finishes eventually.

If you answered _no_ to 2, it means IDeSyDe needs to be extended to identify your models, and therefore your design problem, so that you can get an answer at all.

If you answered _no_ to 1, it means IDeSyDe cannot even know your system model, and thus no identification is ever possible without a proper formal model to perform it on! 
Sadly this means you must first extend ForSyDe IO and _then_ extend IDeSyDe with the proper identification and solution support for it. 
This is the worst case you can encounter, which hopefully becomes ever rarer as the tool and its "design space" library increases.

<!-- > The following sections are out of date! IDeSyDe has migrated to a JVM project. Majorly scala. Proper installation instructions
> are to come. For now you can downloaded the latest jars from the github webpage.

# Installation

## Getting it from PyPI

IDeSyDe is uploaded to PyPI freqeuently, along it's data dependencies such as minizinc files. You can install
it on python 3.7+ via _pip_. On linux the command is, for example,

    python3 -m pip install idesyde
  
which install the latest version of it. Then you can test it with `python3 -m idesyde -h`.

## Standalone python package

You can take a look at the automatically generated python zip in the 
[project's release pages]({{ site.sources.idesyde }}/releases) for _your python version_ and _your OS_.
The reason the match needs to be quite precise resides upon performant dependencies such as [numpy](https://numpy.org/).
They usually ship compiled (or to-be-compiled) C files, which then depend on a handful of factors, of whom
include your python distribution in your OS.

If all goes well, it's a matter of you downloading the `.pyz` files and running it in a terminal via python:

    python3 idesyde.pyz -h

and to test it.

## Standalone executable

Similar to the [python package](#standelone-python-package), you have to find the right executable for your
OS, which will come in `zip` file that you can extract. If all goes well, you should have an executable `idesyde`
with a `lib` folder besides it. _You must move the lib folder with idesyde if you wish to move it_, due to how
the executable loads all the libraries and dependencies.

As before just issue a help command for it to test if all is well and alive:

    ./idesyde -h -->
