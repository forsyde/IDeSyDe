package idesyde.cli

import java.util.concurrent.Callable
import picocli.CommandLine.*
import java.io.File
import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.identification.api.Identification
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.log4j.BasicConfigurator

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

  lazy val logger = LogManager.getLogger(classOf[IDeSyDeCLI])

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

  @Option(
    names = Array("-v", "--verbosity"),
    description = Array("set the verbosity level for logging")
  )
  var verbosityLevel: String = "INFO"

  def call(): Int = {
    // setLoggingLevel(Level.valueOf(verbosityLevel))
    BasicConfigurator.configure()
    logger.info("Set logging levels.")
    val validInputs =
      inputModels.filter(f => f.getName.endsWith("forsyde.xml") || f.getName.endsWith("forxml"))
    if (validInputs.isEmpty) {
      println(
        "At least one input model '.forsyde.xml' | '.forxml' is necessary"
      )
    } else {
      logger.info("Reading and merging input models.")
      val models = validInputs.map(i => ForSyDeModelHandler().loadModel(i))
      val mergedModel = {
        val mhead = models.head
        models.tail.foreach(mhead.mergeInPlace(_))
        mhead
      }
      logger.info("Performing identification on merged model.")
      val identified = Identification.identifyDecisionModels(mergedModel)
      logger.info(s"Identification finished with ${identified.size} decision models.")
    }
    0
  }

  // def setLoggingLevel(loggingLevel: Level) = {
  //   val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext];
  //   val config = ctx.getConfiguration();
  //   val loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
  //   loggerConfig.setLevel(loggingLevel);
  //   ctx.updateLoggers();
  //   BasicConfigurator.configure()
  // }

  // def mergeInputs(inputs: Array[File]): ForSyDeModel = inputs.map(f => ForSyDeModelHandler.loadModel(f.getName)).reduce((m1, m2) => m1.)
}
