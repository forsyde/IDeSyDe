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

This tutorial assumes that you are *at least* familiar with Java syntax and minimally aware of how to structure Java projects.
We will have brief mentions on how the

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
    ...
    maven { url 'https://jitpack.io' }
}
```

And then add to the modules to the dependencies section of your build.gradle:

```
dependencies {
    ...
    implementation "com.github.forsyde.IDeSyDe:build-java-core:0.8.4"
    compileOnly "com.github.forsyde.IDeSyDe:build-java-core-generator:develop-SNAPSHOT"
    annotationProcessor "com.github.forsyde.IDeSyDe:build-java-core-generator:develop-SNAPSHOT"
    ...
}
```

Or any version released after `0.8.4`. Check on [the GitHub release page to see the latest version](https://github.com/forsyde/IDeSyDe/releases).
With this, it is now possible to create the Java module to be used by IDeSyDe!

### Basic module

Using your favourite Java IDE (we recommend either [Intellij IDEA](https://www.jetbrains.com/idea/) or [VS Code](https://code.visualstudio.com/)),
create a new Java `interface` that will correspond to the new Module.
Though you can choose any name you wish, in this tutorial we will go with `ExampleModule` in the package `tutorial`:

```
package tutorial;

public interface ExampleModule {
}
```

In case you are not experienced in Java development, your project should now look like this:

```
├───.gradle
│   ├───8.2
│   │   ├───checksums
│   │   ├───dependencies-accessors
│   │   ├───executionHistory
│   │   ├───fileChanges
│   │   ├───fileHashes
│   │   └───vcsMetadata
│   ├───buildOutputCleanup
│   └───vcs-1
├───.idea
│   └───codeStyles
├───gradle
│   └───wrapper
└───src
    ├───main
    │   ├───java
    │   │   └───tutorial    <----- The new interface file exists inside this folder
    │   └───resources
    └───test
        ├───java
        └───resources
```

The interface created, we can now extend the created interface with the standard IDeSyDe interface `Module`:

```
package tutorial;

import idesyde.core.Module;

public interface ExampleModule extends Module {
}
```

That is all there is to it. From now on, if there is anything (auto) registered to `ExampleModule`, it will be used later by IDeSyDe once a self-contained `jar` is produced.
The self-contained `jar` can be achieved by adding the `shadowJar` plugin in the `build.gradle`:

```
plugins {
    ...
    id 'com.github.johnrengelman.shadow'
    ...
}
```

### Identification Rules

An identification rule is realised in the Java world through a class that implements the interface `IdentificationRule`.
For example, let's create a bare bones identification rule that does not identify anything;
take special notive of the `@AutoRegister` annotation:

```
package tutorial;

import idesyde.core.*;

import java.util.Set;

@AutoRegister(ExampleModule.class)
public class ExampleIdentificationRule implements IdentificationRule {
    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels, Set<? extends DecisionModel> decisionModels) {
        return new IdentificationResult(Set.of(), Set.of());
    }
}
```

There three important items in this template:

 1. The `@AutoRegister(ExampleModule.class)` makes sure that the identification rule class `ExampleIdentificationRule` is auto-wired with the module in the final `jar`, so that IDeSyDe can use it in its runts.
 2. The `IdentificationResult` is a simple data container for a `Set` of new `DecisionModel`s and possible error/warning messages.
 3. You *must* implement the `IdentificationRule` interface for these function-wrapper classes; otherwise, the auto-wiring mechanism will generate uncompilable code.
    In general, Java IDEs is quite good in telling you where the problem is even through you are generting code, but best to avoid these errors in a large codebase.

The design models are the "models" that capture model-based or model-driven elements, e.g. a class wrapping [EMF models](https://eclipse.dev/modeling/emf/).
The decision models are the ones which will contain any **non-decision** analysis results and extract information from design models.
The non-decision part is not an enforced hard requirement: there is no hard checking from the programming side to ensure that you don't have decision-making being done
during identification.
However, if you do this breach of assumption, the identification process which is proven to be polynomial-bounded may suddenly become exponential;
we would rather leave all the exponential decision-making to the exploration procedure.

If you are following this tutorial without any prior IDeSyDe experience, likely you have no design or decision models to go ahead and fill the created
identification rule, so we will address this first.

### Design model

For the sake of exemplification, we will assume that our design model of interest is [AMALTHEA](https://eclipse.dev/app4mc/).
This means that we could create a design model wrapping the java files that already exist from AMALTHEA and simply use them directly.
First, let our project consume the amalthea libraries from maven central:

```
dependencies {
    ...
    implementation 'org.eclipse.app4mc:org.eclipse.app4mc.amalthea.model:3.3.0' 
    ...
}
```

and then create the wrapper design model:

```
package tutorial;

import idesyde.core.DesignModel;
import org.eclipse.app4mc.amalthea.model.Amalthea;

import java.util.Set;

public record AmaltheaDesignModel(
        Amalthea amaltheaModel
) implements DesignModel {

    @Override
    public String category() {
        return "AmaltheaDesignModel";
    }

    @Override
    public String format() {
        return "amxmi";
    }
}
```

where the `category` is an identifier so that other languages might be able to consume this design model (if necessary) from its "opaque" form.
The opaqueness in this case is the DSI term for serialized in a standard format.
Similarly, `format` describes the file format on disk for this design model, which is `amxmi` for XMI files exchanged in App4mc (AMALTHEA) applications.
There is an important information missing in this design model: the elements identifiers that can be identified out of this model.
Thus, we override another method of this interface: `elements`,

```
...
@Override
public Set<String> elements() {
    HashSet<String> elems = new HashSet<>();
    amaltheaModel.getSwModel().getTasks().forEach(task -> elems.add(task.getName()));
    return elems;
}
...
```

In this case, we are saying that the only elements that may be identified (from IDeSyDe's perspective) are the tasks in the AMALTHEA model.
Naturally, this should be much bigger outside the scope of this tutorial; an AMALTHEA model has platform information, timing information, constraints information etc.
Anything that could be identfied out of an AMALTHEA model should appear in the `elements` set generated by this design model.
In the small and pedagogical scope of this tutorial, this is enough.

### Decision model

For this tutorial, let us aim to have a decision model that captures indenpendent periodic task models without the instrumentation data, i.e. without worst-case execution times (WCET).
This means that every single one of our tasks can be described as a triple `(Period, Relative deadline, Offset)`.
So let's go ahead and make a decision model that captures exactly this information:

```
package tutorial;

import idesyde.core.DecisionModel;

import java.util.Set;

public record IndependentPeriodicTasks(
        List<String> tasks,
        List<Double> periods,
        List<Double> realtiveDeadlines,
        List<Double> offsets
) implements DecisionModel {

    @Override
    public String category() {
        return "IndependentPeriodicTasks";
    }

    
}
```

## Rust embedded module

TBD
