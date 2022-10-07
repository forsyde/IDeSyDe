package idesyde.cli

import java.io.File
import java.nio.file.Paths
import scribe.format.FormatterInterpolator
import scribe.Level
import idesyde.IDeSyDeStandalone


class IDeSyDeCLIParser extends scopt.OptionParser[IDeSyDeRunConfig]("idesyde"):
    head(
        """
          ___  ___        ___        ___
         |_ _||   \  ___ / __| _  _ |   \  ___ 
          | | | |) |/ -_)\__ \| || || |) |/ -_)
         |___||___/ \___||___/ \_, ||___/ \___|
                               |__/

        Automated Identification and Exploration of Design Spaces in ForSyDe
        """
    )
    arg[File]("<inputModel> [<inputModel> ...]")
        .unbounded()
        .action((f, x) => x.copy(inputModelsPaths = x.inputModelsPaths.appended(f.toPath)))

    opt[File]('o', "out")
        .text("If the output is an existing directory, write all solutions to the directory. Otherwise, the lastest solution is written to the destination.")
        .valueName("<outputModel>")
        .action((f, x) => x.copy(outputModelPath = f.toPath))
    
    opt[String]("decision-model")
        .text("Filters the allowed decision models to be chosen after identification. All identified are chosen if none is specified.")
        .valueName("<DecisionModelID>")
        .action((f, x) => x.copy(allowedDecisionModels = x.allowedDecisionModels.appended(f)))

    opt[String]('v', "verbosity")
        .valueName("<verbosityLevel>")
        .action((v, x) => {
            IDeSyDeStandalone.setLoggingLevel(Level.get(v).getOrElse(Level.Info))
            x
        })

    opt[Int]("solutions-limit")
        .valueName("<solutionsLimits>")
        .action((v, x) => x.copy(solutionLimiter = v))


end IDeSyDeCLIParser