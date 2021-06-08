package idesyde.cli

import java.util.concurrent.Callable
import picocli.CommandLine.*
import java.io.File

@Command(
  name = "idesyde",
  mixinStandardHelpOptions = true,
  description = Array("""
  ___  ___        ___        ___
 |_ _||   \  ___ / __| _  _ |   \  ___ 
  | | | |) |/ -_)\__ \| || || |) |/ -_)
 |___||___/ \___||___/ \_, ||___/ \___|
                       |__/

Automated Identification and Exploration of Design Spaces in ForSyDe
""")
)
class IDeSyDeCLI extends Callable[Int] {

  @Parameters(
    paramLabel = "Input Model",
    description = Array("input models to perform analysis")
  )
  var inputModels: Seq[File] = Seq()

  @Option(
    names = Array("-o", "--output"),
    description = Array("output model to output after analysis")
  )
  var outputModel: File = File("forsyde-output.forxml")

  def call(): Int = {
    0
  }
}
