package idesyde.cli

import java.util.concurrent.Callable
import picocli.CommandLine.*
import java.io.File
import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.identification.api.Identification
import scribe.Level
import idesyde.identification.interfaces.MiniZincDecisionModel
import idesyde.exploration.api.Exploration
import scala.concurrent.ExecutionContext
import java.nio.file.Path
import java.nio.file.Paths

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
class IDeSyDeCLI extends Callable[Int]:

  given ExecutionContext = ExecutionContext.global

  @Parameters(
    paramLabel = "Input Model",
    description = Array("input models to perform analysis")
  )
  var inputModels: Array[Path] = Array()

  @Option(
    names = Array("-o", "--output"),
    description = Array("output model to output after analysis")
  )
  var outputModel: Path = Paths.get("forsyde-output.forxml")

  @Option(
    names = Array("-v", "--verbosity"),
    description = Array("set the verbosity level for logging")
  )
  var verbosityLevel: String = "INFO"

  def call(): Int = {
    setLoggingLevel(Level.get(verbosityLevel).getOrElse(Level.Info))
    val modelHandler = ForSyDeModelHandler()
    val validInputs =
      inputModels.filter(f => modelHandler.canLoadModel(f))
    if (validInputs.isEmpty) {
      println(
        "At least one input model '.forsyde.xml' | '.forxml' is necessary"
      )
    } else {
      scribe.info("Reading and merging input models.")
      val model = validInputs.map(i => modelHandler.loadModel(i))
        .foldLeft(ForSyDeModel())(
          (merged, m) => 
            merged.mergeInPlace(m)
            merged
        )

      val identified = Identification.identifyDecisionModels(model)
      scribe.info(s"Identification finished with ${identified.size} decision model(s).")
      val chosen = Exploration.chooseExplorersAndModels(identified)
      scribe.info(s"Total of ${chosen.size} combo of decision model(s) and explorer(s) chosen.")
      // identified.foreach(m => m match {
      //   case mzn: MiniZincDecisionModel => scribe.debug(s"mzn model: ${mzn.mznInputs.toString}")
      // })
      val (explorer, decisionModel) = chosen.head
      val results                   = explorer.explore(decisionModel)
    }
    0
  }

  def setLoggingLevel(loggingLevel: Level) =
    scribe.Logger.root
      .clearHandlers()
      .clearModifiers()
      .withHandler(minimumLevel = Some(loggingLevel))
      .replace()
    scribe.info(s"logging levels set to ${loggingLevel.name}.")

end IDeSyDeCLI
