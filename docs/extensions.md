---
layout: default
title: IDeSyDe - Extensions
description: { { site.description } }
permalink: /extensions
usemathjax: true
---


# Extensions

This page contains two tutorials on how to construct extensions for IDeSyDe in two different manners:
one for JVM languages, but using Java as the main example; and one for a Rust embedded module.

## Java module

To create a new java module, first make sure that JDK and `gradle` are installed in your computer.
You can follow the steps from the [ForSyDe IO website](https://forsyde.github.io/forsyde-io/usage/#optional-downloading-sdk-gradle-and-jabba)
using SDKMan and Jabba.

Once `gradle` is installed, go to a folder in your computer where you wish to develop and build the module and issue:

```
gradle init
```

which ask you what type of project you wish to create and then create a handful of files in the directory that we will work with.
Ideally you should choose `application` as the most basic one, in case you do not fully know how to work with Maven/Gradle based JVM projects.
Among the questions asked, you can choose the default for most, except for the minimum java version, which should be 17.
Don’t worry if this number seems high to you, it is the latest LTS after 11, and it will be supported for many years;
it is just that Java moves slowly but surely, specially its using companies.

The most import file for now is `build.gradle`, which declaratively defines the project’s dependencies and also fetches them.
To fetch the framework parts of IDeSyDe, you need to first enable JitPack as a repository:

```
repositories {
    maven { url '<https://jitpack.io>' }
}
```

And then add to the modules to the dependencies section of your build.gradle:

## Rust embedded module
