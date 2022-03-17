---
layout: default
title: Quick start
nav_order: 1
permalink: /usage/quickstart
parent: Usage
---

# Quick start

As a majorly JVM-based project, the easiest way to start is to jave a Java virtual machine dsitribution installed
in your computer. There are many options, and IDeSyDe is [CI-tested]({{ site.sources.idesyde }}/actions/workflows/test-build.yml)
against graalvm and openjdk. Amazon Correto should work fine too.

Then, you download the `jar` file and make it available to be called from your terminal; IDeSyDe is a CLI tool. You have succeed if
you can execute the following:

    > java -jar idesyde.jar         
    Error: Missing argument <inputModel> [<inputModel> ...]
    
              ___  ___        ___        ___
             |_ _||   \  ___ / __| _  _ |   \  ___ 
              | | | |) |/ -_)\__ \| || || |) |/ -_)
             |___||___/ \___||___/ \_, ||___/ \___|
                                   |__/
    
            Automated Identification and Exploration of Design Spaces in ForSyDe
            
    Usage: idesyde [options] <inputModel> [<inputModel> ...]
    
      <inputModel> [<inputModel> ...]
      -o, --out <outputModel>  If the output is an existing directory, write all solutions to the directory. Otherwise, the lastest solution is output.
      -v, --verbosity <verbosityLevel>

The jar can be downloaded from the [IDeSyDe's releases page]({{ site.sources.idesyde }}/releases). 
Note that the name might note be exactly `idesyde`, but `idesyde-x.x.x-cli.jar`.

## Optional dependencies

IDeSyDe might use [MiniZinc](https://www.minizinc.org/) as the explorer for some of the design space exploration
scenarions that can be identified. Make sure that minzinc is usable from your terminal so that IDeSyDe is also
able to reach it. For UNIX like systems, this translate to having `minizinc` in your PATH so that, calling `minizinc`,
yields:

    > minizinc   
    minizinc: MiniZinc driver.
    Usage: minizinc  [<options>] [-I <include path>] <model>.mzn [<data>.dzn ...] or just <flat>.fzn
    More info with "minizinc --help"

## Performing DSE

IDeSyDe currently consumes ForSyDe IO XMI files as its input. The conventional extension is `forsyde.xmi`!
Please note that this support comes directly from [ForSyDe IO]({{ site.projects.forsydeio }}), and this
requires the extension to be either `forsyde.xmi` or `forxmi` to work.

If the input models you have are not ForSyDe IO XMI files, 
then you might be able to use [ConverSyDe]({{site.projects.forsydeio}}/usage/conversyde) to convert them as such.

Once you have the input files `model1.forsyde.xmi`, `model2.forsyde.xmi`, etc. Then you just execute the idesyde
`jar` with them as inputs:

    > java -jar idesyde.jar \
        -o latest_solved_model.forsyde.xmi \
        model1.forsyde.xmi \
        model2.forsyde.xmi ...

you should see an output like this:

    2022.03.17 13:41:59:541 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.setLoggingLevel:85 - logging levels set to INFO.
    2022.03.17 13:41:59:620 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:39 - Reading and merging input models.
    2022.03.17 13:42:00:347 [main      ] [INFO ] idesyde.identification.api.IdentificationHandler.identifyDecisionModels:47 - Performing identification with 9 rules up to 243 iterations.
    2022.03.17 13:42:01:114 [main      ] [INFO ] idesyde.identification.api.IdentificationHandler.identifyDecisionModels:72 - droppped 3 dominated decision model(s).
    2022.03.17 13:42:01:115 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:48 - Identification finished with 2 decision model(s).
    2022.03.17 13:42:01:121 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:51 - Total of 1 combo of decision model(s) and explorer(s) chosen.
    ...
    2022.03.17 13:42:12:666 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:73 - Finished exploration with 100 solution(s)


Althought it make take some time to finish (up to 100 solutions are currently set as the limit), you can follow in your computer that
the file `latest_solved_model.forsyde.xmi` set as output already existed longer before IDeSyDe finished execution. That's because
intermediate (feasible) solutions are also output by the tools as it tries to optimize it further.

## Difference between no DSE and no feasible results

Since IDeSyDe is a generic DSE tool, your system input might be yet part of the scenarios IDeSyDe knows how to solve.
That can be distinguished by the output of the tool (in case you have not put a verbosity level too low);
If no **combo** of decision model and explorer has been chosen, then IDeSyDe either does not known how to solve
your design scenario (given as a ForSyDe IO Model) or lacks the exploration algorithm to do so.
Otherwise, if a **combo** is chosen, and the result is no solution, then it means the system design given
to IDeSyDe is infeasible. At very least, it is analytically infeasible with the algorithms and equations
programmed in IDeSyDe.

Here's an example of when there's no possible DSE **combo** identified:

    > java -jar idesyde.jar -o solved.forsyde.xmi input_model.forsyde.xmi 
    2022.03.17 14:45:33:242 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.setLoggingLevel:85 - logging levels set to INFO.
    2022.03.17 14:45:33:323 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:39 - Reading and merging input models.
    2022.03.17 14:45:33:595 [main      ] [INFO ] idesyde.identification.api.IdentificationHandler.identifyDecisionModels:47 - Performing identification with 9 rules up to 162 iterations.
    2022.03.17 14:45:33:767 [main      ] [INFO ] idesyde.identification.api.IdentificationHandler.identifyDecisionModels:72 - droppped 0 dominated decision model(s).
    2022.03.17 14:45:33:768 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:48 - Identification finished with 1 decision model(s).
    2022.03.17 14:45:33:773 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:51 - Total of 0 combo of decision model(s) and explorer(s) chosen.
    2022.03.17 14:45:33:775 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:75 - Finished exploration with no solution

And here's an example where there's a DSE **combo** to be solved but not feasible solution exists:

    > java -jar idesyde.jar -o solved.forsyde.xmi input_system.forsyde.xmi
    2022.03.17 14:49:24:516 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.setLoggingLevel:85 - logging levels set to INFO.
    2022.03.17 14:49:24:619 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:39 - Reading and merging input models.
    2022.03.17 14:49:25:315 [main      ] [INFO ] idesyde.identification.api.IdentificationHandler.identifyDecisionModels:47 - Performing identification with 9 rules up to 243 iterations.
    2022.03.17 14:49:26:215 [main      ] [INFO ] idesyde.identification.api.IdentificationHandler.identifyDecisionModels:72 - droppped 3 dominated decision model(s).
    2022.03.17 14:49:26:216 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:48 - Identification finished with 2 decision model(s).
    2022.03.17 14:49:26:224 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:51 - Total of 1 combo of decision model(s) and explorer(s) chosen.
    2022.03.17 14:49:32:388 [main      ] [INFO ] idesyde.cli.IDeSyDeRunConfig.run:75 - Finished exploration with no solution


<!-- ## Pre requisites

Whichever way you use to get IDeSyDe installed, you also need to have 
installed in your system so that IDeSyDe can call it!
There's a catch, however: The latest version might contain a bug that prevents some models in IDeSyDe
for running properly, therefore, the tested and recommended version is
[Minizinc 2.4.3](https://github.com/MiniZinc/MiniZincIDE/releases/tag/2.4.3).

If you don't want to try the standlone executables, you also need to have Python 3.7+ installed in your system. -->
