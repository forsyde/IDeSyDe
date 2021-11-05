package idesyde.cli

import java.io.File
import java.nio.file.Paths

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
        .minOccurs(1)
        .unbounded()
        .action((f, x) => x.copy(inputModelsPaths = x.inputModelsPaths.appended(f.toPath)))

    opt[Seq[File]]('o', "out")
        .valueName("<outputModel1>[,<outputModel2>,...")
        .minOccurs(1)
        .unbounded()
        .action((f, x) => x.copy(outputModelPaths = f.map(_.toPath)))

    opt[String]('v', "verbosity")
        .valueName("<verbosityLevel>")
        .action((v, x) => x.copy(verbosityLevel = v))


end IDeSyDeCLIParser