package idesyde.cli

import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable.Buffer
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.identification.IdentificationHandler
import idesyde.exploration.ExplorationHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.exploration.ChocoExplorationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.DecisionModel
import idesyde.utils.SimpleStandardIOLogger
import idesyde.utils.Logger

case class IDeSyDeRunConfig(
    var inputModelsPaths: Buffer[Path] = Buffer.empty,
    var outputModelPath: Path = Paths.get("idesyde-out.fiodl"),
    var allowedDecisionModels: Buffer[String] = Buffer(),
    var solutionLimiter: Int = 0,
    val explorationTimeOutInSecs: Long = 0L
)(using logger: Logger) {

  val explorationHandler = ExplorationHandler()
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler()
    .registerIdentificationRule(CommonIdentificationModule())
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  def run(): Unit =
    val modelHandler = ForSyDeModelHandler()
    val validInputs =
      inputModelsPaths.map(f => (f, modelHandler.canLoadModel(f)))
    if (validInputs.forall((p, b) => !b)) {
      logger.error(
        "At least one valid model is necessary"
      )
    } else if (validInputs.exists((p, b) => !b)) {
      logger.error("These inputs are invalid (unknown format): " + validInputs.filter((p, b) => b).map((p, b) => p.getFileName()).mkString(", "))
    } else {
      logger.info("Reading and merging input models.")
      val model = ForSyDeDesignModel(validInputs
        .map((p, _) => modelHandler.loadModel(p))
        .foldLeft(ForSyDeSystemGraph())((merged, m) =>
          merged.mergeInPlace(m)
          merged
        ))

      val identified = identificationHandler.identifyDecisionModels(Set(model))
      logger.info(s"Identification finished with ${identified.size} decision model(s).")
      if (identified.size > 0)
        val chosen = explorationHandler.chooseExplorersAndModels(identified)
        val chosenFiltered =
          if (allowedDecisionModels.size > 0) then
            chosen.filter((exp, dm) => allowedDecisionModels.contains(dm.uniqueIdentifier))
          else chosen
        logger.info(s"Total of ${chosenFiltered.size} combo of decision model(s) and explorer(s) chosen.")
        // identified.foreach(m => m match {
        //   case mzn: MiniZincForSyDeDecisionModel => scribe.debug(s"mzn model: ${mzn.mznInputs.toString}")
        // })
        if (chosenFiltered.size > 1) {
          logger.warn(s"Taking a random decision model and explorer combo.")
        }
        val numSols = chosenFiltered
          .headOption
          .map((explorer, decisionModel) =>
            explorer
              .explore(decisionModel, explorationTimeOutInSecs)
              .flatMap(identificationHandler.integrateDecisionModel(model, _))
              .flatMap(result => result match {
                case fdm: ForSyDeDesignModel => Some(fdm.systemGraph)
                case _ => Option.empty
              })
              .scanLeft(0)((res, result) => {
                if (!outputModelPath.toFile.exists || outputModelPath.toFile.isFile) then
                  logger.debug(s"writing solution at ${outputModelPath.toString}")
                  modelHandler.writeModel(model.systemGraph.merge(result), outputModelPath)
                else if (outputModelPath.toFile.exists && outputModelPath.toFile.isDirectory) then
                  val outPath = outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                  logger.debug(s"writing solution at ${outPath.toString}")
                  modelHandler.writeModel(
                    model.systemGraph.merge(result),
                    outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                  )
                res + 1
              }).takeWhile(res => (solutionLimiter <= 0) || (solutionLimiter > 0 && res <= solutionLimiter)).last
          )
          .getOrElse(0)
        if (numSols > 0)
          logger.info(s"Finished exploration with ${numSols} solution(s)")
        else
          logger.info(s"Finished exploration with no solution")
      //scribe.info("Finished successfully")
    }

}
