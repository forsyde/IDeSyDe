package idesyde.cli

import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable.Buffer
import scribe.Level
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.identification.IdentificationHandler
import idesyde.exploration.ExplorationHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.exploration.ChocoExplorationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import idesyde.identification.forsyde.api.ForSyDeIdentificationModule
import idesyde.identification.minizinc.api.MinizincIdentificationModule
import scribe.format.FormatterInterpolator
import idesyde.identification.DecisionModel

case class IDeSyDeRunConfig(
    var inputModelsPaths: Buffer[Path] = Buffer.empty,
    var outputModelPath: Path = Paths.get("idesyde-result.forsyde.xml"),
    var allowedDecisionModels: Buffer[String] = Buffer(),
    var verbosityLevel: String = "INFO",
    executionContext: ExecutionContext
) {

  val explorationHandler = ExplorationHandler(
    infoLogger = (s: String) => scribe.info(s),
    debugLogger = (s: String) => scribe.debug(s)
  )
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler(
    infoLogger = (s: String) => scribe.info(s),
    debugLogger = (s: String) => scribe.debug(s)
  )
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  def run(): Unit =
    setLoggingLevel(Level.get(verbosityLevel).getOrElse(Level.Info))
    val modelHandler = ForSyDeModelHandler()
    val validInputs =
      inputModelsPaths.map(f => (f, modelHandler.canLoadModel(f)))
    if (validInputs.forall((p, b) => !b)) {
      scribe.error(
        "At least one valid model is necessary"
      )
    } else if (validInputs.exists((p, b) => !b)) {
      scribe.error("These inputs are invalid (unknown format): " + validInputs.filter((p, b) => b).map((p, b) => p.getFileName()).mkString(", "))
    } else {
      scribe.info("Reading and merging input models.")
      val model = validInputs
        .map((p, _) => modelHandler.loadModel(p))
        .foldLeft(ForSyDeSystemGraph())((merged, m) =>
          merged.mergeInPlace(m)
          merged
        )

      val identified = identificationHandler.identifyDecisionModels(model)
      scribe.info(s"Identification finished with ${identified.size} decision model(s).")
      if (identified.size > 0)
        val chosen = explorationHandler.chooseExplorersAndModels(identified)
        val chosenFiltered =
          if (allowedDecisionModels.size > 0) then
            chosen.filter((exp, dm) => allowedDecisionModels.contains(dm.uniqueIdentifier))
          else chosen
        scribe.info(s"Total of ${chosenFiltered.size} combo of decision model(s) and explorer(s) chosen.")
        // identified.foreach(m => m match {
        //   case mzn: MiniZincForSyDeDecisionModel => scribe.debug(s"mzn model: ${mzn.mznInputs.toString}")
        // })
        //var numSols = 0
        val numSols = chosenFiltered.headOption
          .filter((e, decisionModel) =>
            e.isInstanceOf[ForSyDeIOExplorer] && decisionModel.isInstanceOf[ForSyDeDecisionModel]
          )
          .map((e, m) => (e.asInstanceOf[ForSyDeIOExplorer], m.asInstanceOf[ForSyDeDecisionModel]))
          .map((explorer, decisionModel) =>
            explorer
              .explore(decisionModel)(using executionContext)
              .foldLeft(0)((res, result) => {
                if (!outputModelPath.toFile.exists || outputModelPath.toFile.isFile) then
                  scribe.debug(s"writing solution at ${outputModelPath.toString}")
                  modelHandler.writeModel(model.merge(result), outputModelPath)
                else if (outputModelPath.toFile.exists && outputModelPath.toFile.isDirectory) then
                  val outPath = outputModelPath.resolve(Paths.get(s"solution_${res.toString}"))
                  scribe.debug(s"writing solution at ${outPath.toString}")
                  modelHandler.writeModel(
                    model.merge(result),
                    outputModelPath.resolve(Paths.get(s"solution_${res.toString}"))
                  )
                res + 1
              })
          )
          .getOrElse(0)
        if (numSols > 0)
          scribe.info(s"Finished exploration with ${numSols} solution(s)")
        else
          scribe.info(s"Finished exploration with no solution")
      //scribe.info("Finished successfully")
    }

  def setLoggingLevel(loggingLevel: Level) =
    if (loggingLevel == Level.Debug)
      scribe.Logger.root
        .clearHandlers()
        .clearModifiers()
        .withHandler(
          minimumLevel = Some(loggingLevel),
          formatter =
            formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format
              .italic(scribe.format.classNameSimple)} - ${scribe.format.messages}"
        )
        .replace()
    else
      scribe.Logger.root
        .clearHandlers()
        .clearModifiers()
        .withHandler(
          minimumLevel = Some(loggingLevel),
          formatter =
            formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.messages}"
        )
        .replace()
    scribe.info(s"logging levels set to ${loggingLevel.name}.")

}
