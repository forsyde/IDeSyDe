package idesyde.cli

import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable.Buffer
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.core.DecisionModel
import idesyde.utils.SimpleStandardIOLogger
import idesyde.utils.Logger
import idesyde.exploration.CanExplore
import idesyde.identification.CanIdentify
import idesyde.exploration.ExplorationModule
import idesyde.identification.IdentificationModule
import upickle.default.*
import idesyde.core.ParametricDecisionModel
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64

case class IDeSyDeRunConfig(
    val identificationModules: Set[IdentificationModule],
    val explorationModules: Set[ExplorationModule],
    var inputModelsPaths: Buffer[Path] = Buffer.empty,
    var outputModelPath: Path = Paths.get("idesyde-out.fiodl"),
    var allowedDecisionModels: Buffer[String] = Buffer(),
    var solutionLimiter: Int = 0,
    val explorationTimeOutInSecs: Long = 0L
)(using logger: Logger)
    extends CanExplore
    with CanIdentify {

  def run(): Unit =
    val sortedPaths = inputModelsPaths.sortBy(_.toString())
    val messageDigest = MessageDigest.getInstance("SHA-1")
    messageDigest.reset()
    val digested = messageDigest.digest(sortedPaths.flatMap(_.toString().map(_.toByte)).toArray)
    val stringOfDigested = Base64.getEncoder().encodeToString(digested)
    val runPath = Paths.get("run").resolve(stringOfDigested)
    logger.info(s"Run folder: ${runPath.toString()}")
    val exploredPath =  runPath.resolve("explored")
    val identifiedPath = runPath.resolve("identified")
    Files.createDirectories(exploredPath)
    Files.createDirectories(identifiedPath)
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

      val identified = identifyDecisionModels(Set(model), identificationModules)
      logger.info(s"Identification finished with ${identified.size} decision model(s).")
      if (identified.size > 0)
        // save the identified models
        for (model <- identified) {
          Files.writeString(identifiedPath.resolve(Paths.get(s"${model.uniqueIdentifier}_header.json")), model.header.asText)
          model match {
            case parametric : ParametricDecisionModel[?] => 
              Files.writeString(identifiedPath.resolve(Paths.get(s"${model.uniqueIdentifier}_body.json")), parametric.bodyAsText)
            case _ =>
          }
        }
        // now continue with flow
        val chosen = chooseExplorersAndModels(identified, explorationModules)
        val chosenFiltered =
          if (allowedDecisionModels.size > 0) then
            chosen.filter((exp, dm) => allowedDecisionModels.contains(dm.uniqueIdentifier))
          else chosen
        logger.info(
          s"Total of ${chosenFiltered.size} combo of decision model(s) and explorer(s) chosen."
        )
        // identified.foreach(m => m match {
        //   case mzn: MiniZincForSyDeDecisionModel => scribe.debug(s"mzn model: ${mzn.mznInputs.toString}")
        // })
        if (chosenFiltered.size > 1) {
          logger.warn(s"Taking a random decision model and explorer combo.")
        }
        val numSols = chosenFiltered.headOption
          .map((explorer, decisionModel) =>
            explorer
              .explore(decisionModel, explorationTimeOutInSecs)
              .zipWithIndex
              .map((decisionModel, num) => {
                Files.writeString(exploredPath.resolve(
                    Paths.get(s"${num}_${decisionModel.uniqueIdentifier}_header.json")
                  ), decisionModel.header.asText)
                decisionModel match {
                  case parametric : ParametricDecisionModel[?] => 
                    Files.writeString(exploredPath.resolve(
                    Paths.get(s"${num}_${decisionModel.uniqueIdentifier}_body.json")
                  ), parametric.bodyAsText)
                  case _ =>
                }
                (decisionModel, num)
              })
              .flatMap((m, res) =>
                integrateDecisionModel(model, m, identificationModules).map((_, res))
              )
              .flatMap((m, res) =>
                m match {
                  case fdm: ForSyDeDesignModel => Some((fdm.systemGraph, res))
                  case _                       => Option.empty
                }
              )
              .map((m, res) => {
                if (!outputModelPath.toFile.exists || outputModelPath.toFile.isFile) then
                  logger.debug(s"writing solution at ${outputModelPath.toString}")
                  modelHandler.writeModel(model.systemGraph.merge(m), outputModelPath)
                else if (outputModelPath.toFile.exists && outputModelPath.toFile.isDirectory) then
                  val outPath =
                    outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                  logger.debug(s"writing solution at ${outPath.toString}")
                  modelHandler.writeModel(
                    model.systemGraph.merge(m),
                    outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                  )
                res
              })
              .takeWhile(res =>
                (solutionLimiter <= 0) || (solutionLimiter > 0 && res <= solutionLimiter)
              )
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
