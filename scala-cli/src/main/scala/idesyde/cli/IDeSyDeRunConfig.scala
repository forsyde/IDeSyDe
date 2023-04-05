package idesyde.cli

import scala.collection.mutable.Buffer
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.core.DecisionModel
import idesyde.utils.SimpleStandardIOLogger
import idesyde.utils.Logger
import idesyde.exploration.CanExplore
import idesyde.identification.CanIdentify
import idesyde.core.ExplorationLibrary
import idesyde.core.IdentificationLibrary
import upickle.default.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.nio.file.Path
import java.nio.file.Paths
import idesyde.core.CompleteDecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.headers.ExplorationCombinationHeader
import idesyde.core.headers.DesignModelHeader
import idesyde.blueprints.ExplorationModule
import idesyde.forsydeio.ForSyDeDesignModel
import idesyde.blueprints.ModuleUtils

case class IDeSyDeRunConfig(
    val identificationModules: Set[IdentificationLibrary],
    val explorationModules: Set[ExplorationModule],
    var inputModelsPaths: Buffer[Path] = Buffer.empty,
    var outputModelPath: Path = Paths.get("idesyde-out.fiodl"),
    var allowedDecisionModels: Buffer[String] = Buffer(),
    var solutionLimiter: Int = 0,
    val explorationTimeOutInSecs: Long = 0L
)(using logger: Logger)
    extends CanExplore
    with CanIdentify
    with ModuleUtils {

  def run(): Unit =
    val sortedPaths   = inputModelsPaths.sortBy(_.toString())
    val messageDigest = MessageDigest.getInstance("SHA-1")
    messageDigest.reset()
    val digested = messageDigest.digest(sortedPaths.flatMap(_.toString().map(_.toByte)).toArray)
    val stringOfDigested = Base64.getEncoder().encodeToString(digested)
    val runPath          = os.pwd / "run"
    logger.info(s"Run folder: ${runPath.toString()}")
    val inputsPath     = runPath / "inputs"
    val exploredPath   = runPath / "explored"
    val identifiedPath = runPath / "identified"
    val explorablePath = runPath / "explorable"
    val outputsPath    = runPath / "outputs"
    os.makeDir.all(inputsPath)
    os.makeDir.all(exploredPath)
    os.makeDir.all(identifiedPath)
    os.makeDir.all(explorablePath)
    os.makeDir.all(outputsPath)
    val modelHandler = ForSyDeModelHandler()
    val validForSyDeInputs =
      inputModelsPaths.map(f => (f, modelHandler.canLoadModel(f)))
    if (validForSyDeInputs.forall((p, b) => !b)) {
      logger.error(
        "At least one valid model is necessary"
      )
    } else if (validForSyDeInputs.exists((p, b) => !b)) {
      logger.error(
        "These inputs are invalid (unknown format): " + validForSyDeInputs
          .filter((p, b) => b)
          .map((p, b) => p.getFileName())
          .mkString(", ")
      )
    } else {
      logger.info("Reading and merging input models.")
      val model = ForSyDeDesignModel(
        validForSyDeInputs
          .map((p, _) => modelHandler.loadModel(p))
          .foldLeft(ForSyDeSystemGraph())((merged, m) =>
            merged.mergeInPlace(m)
            merged
          )
      )
      // save the design models
      model.writeToPath(inputsPath, "", "IDeSyDeStandaline")
      val identified = identifyDecisionModels(Set(model), identificationModules)
      logger.info(s"Identification finished with ${identified.size} decision model(s).")
      if (identified.size > 0)
        // save the identified models
        for ((dm, i) <- identified.zipWithIndex) {
          dm.writeToPath(identifiedPath, "internal", "IDeSyDeStandalone")
        }
        // now continue with flow
        val chosen = chooseExplorersAndModels(
          identified,
          explorationModules.map(_.asInstanceOf[ExplorationLibrary])
        )
        val chosenFiltered =
          if (allowedDecisionModels.size > 0) then
            chosen.filter((e, dm) => allowedDecisionModels.contains(dm.uniqueIdentifier))
          else chosen
        logger.info(
          s"Total of ${chosenFiltered.size} combo of decision model(s) and explorer(s) chosen."
        )
        if (chosenFiltered.size > 1) {
          logger.warn(s"Taking a random decision model and explorer combo.")
        }
        val numSols = chosenFiltered.headOption
          .map((explorer, decisionModel) =>
            explorer
              .explore(decisionModel, explorationTimeOutInSecs)
              .zipWithIndex
              .map((decisionModel, num) => {
                decisionModel.writeToPath(exploredPath, f"$num%32d", "IDeSyDeStandalone")
                (decisionModel, num)
              })
              .flatMap((m, res) =>
                integrateDecisionModel(model, m, identificationModules).map((_, res))
              )
              .map((m, res) =>
                m.writeToPath(outputsPath, f"$res%32d", "IDeSyDeStandalone")
                m match {
                  case fdm @ ForSyDeDesignModel(m) =>
                    if (!outputModelPath.toFile.exists || outputModelPath.toFile.isFile) then
                      logger.debug(s"writing solution at ${outputModelPath.toString}")
                      modelHandler.writeModel(model.systemGraph.merge(m), outputModelPath)
                    else if (
                      outputModelPath.toFile.exists && outputModelPath.toFile.isDirectory
                    ) then
                      val outPath =
                        outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                      logger.debug(s"writing solution at ${outPath.toString}")
                      modelHandler.writeModel(
                        model.systemGraph.merge(m),
                        outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                      )
                  case _ =>
                }
                (m, res)
              )
              .takeWhile((m, res) =>
                (solutionLimiter <= 0) || (solutionLimiter > 0 && res <= solutionLimiter)
              )
              .map((_, n) => n)
              .last
          )
          .getOrElse(0)
        if (numSols > 0)
          logger.info(s"Finished exploration with ${numSols} solution(s)")
        else
          logger.info(s"Finished exploration with no solution")
      //scribe.info("Finished successfully")
    }

}
