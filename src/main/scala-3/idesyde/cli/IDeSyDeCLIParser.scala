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
        .unbounded()
        .action((f, x) => x.copy(inputModelsPaths = x.inputModelsPaths.appended(f.toPath)))

    opt[File]('o', "out")
        .valueName("<outputModel>")
        .action((f, x) => x.copy(outputModelPath = f.toPath))

    opt[String]('v', "verbosity")
        .valueName("<verbosityLevel>")
        .action((v, x) => x.copy(verbosityLevel = v))


end IDeSyDeCLIParser