package idesyde.cli

import java.util.concurrent.Callable
import picocli.CommandLine.*
import java.io.File
import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.drivers.ForSyDeModelHandler

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
  var inputModels: Array[File] = Array()

  @Option(
    names = Array("-o", "--output"),
    description = Array("output model to output after analysis")
  )
  var outputModel: File = File("forsyde-output.forxml")

  def call(): Int = {
    val validInputs = inputModels.filter(f =>
      f.getName.endsWith("forsyde.xml") || f.getName.endsWith("forxml")
    )
    if (validInputs.isEmpty) {
      println(
        "At least one input model '.forsyde.xml' | '.forxml' is necessary"
      )
    } else {}
    0
  }

  //def mergeInputs(inputs: Array[File]): ForSyDeModel = inputs.map(f => ForSyDeModelHandler.loadModel(f.getName)).reduce((m1, m2) => m1.)
}
