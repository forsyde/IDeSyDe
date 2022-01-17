package idesyde.cli

import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable.Buffer
import scribe.Level
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.identification.api.Identification
import idesyde.exploration.api.Exploration
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext

case class IDeSyDeRunConfig (
    inputModelsPaths: Buffer[Path] = Buffer.empty,
    outputModelPath: Path = Paths.get("idesyde-result.forsyde.xml"),
    verbosityLevel: String = "INFO",
    executionContext: ExecutionContext
):

    def run(): Unit = 
        setLoggingLevel(Level.get(verbosityLevel).getOrElse(Level.Info))
        val modelHandler = ForSyDeModelHandler()
        val validInputs =
          inputModelsPaths.filter(f => modelHandler.canLoadModel(f))
        if (validInputs.isEmpty) {
          println(
            "At least one valid model is necessary"
          )
        } else {
          scribe.info("Reading and merging input models.")
          val model = validInputs.map(i => modelHandler.loadModel(i))
            .foldLeft(ForSyDeSystemGraph())(
              (merged, m) => 
                merged.mergeInPlace(m)
                merged
            )

          val identified = Identification.identifyDecisionModels(model)
          scribe.info(s"Identification finished with ${identified.size} decision model(s).")
          if (identified.size > 0)
            val chosen = Exploration.chooseExplorersAndModels(identified)
            scribe.info(s"Total of ${chosen.size} combo of decision model(s) and explorer(s) chosen.")
            // identified.foreach(m => m match {
            //   case mzn: MiniZincDecisionModel => scribe.debug(s"mzn model: ${mzn.mznInputs.toString}")
            // })
            val (explorer, decisionModel) = chosen.head
            val results                   = explorer.explore(decisionModel)(using executionContext)
            var numSols = 0
            results.foreach(result =>
              scribe.debug(s"writing solution at ${outputModelPath.toString}")
              modelHandler.writeModel(model.merge(result), outputModelPath)
              numSols += 1
            )
            if (numSols > 0)
              scribe.info(s"Finished exploration with ${numSols} solution(s)")
            else
              scribe.info(s"Finished exploration with no solution")
          scribe.info("Finished successfully")
        }

    def setLoggingLevel(loggingLevel: Level) =
        scribe.Logger.root
        .clearHandlers()
        .clearModifiers()
        .withHandler(minimumLevel = Some(loggingLevel))
        .replace()
        scribe.info(s"logging levels set to ${loggingLevel.name}.")

end IDeSyDeRunConfig