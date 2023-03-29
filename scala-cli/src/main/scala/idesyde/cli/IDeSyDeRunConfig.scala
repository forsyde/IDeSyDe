package idesyde.cli

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
import idesyde.core.ExplorationLibrary
import idesyde.core.IdentificationLibrary
import upickle.default.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.nio.file.Path
import java.nio.file.Paths
import idesyde.core.CompleteDecisionModel
import idesyde.core.ExplorationCombination
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.headers.ExplorationCombinationHeader
import idesyde.core.headers.DesignModelHeader
import idesyde.blueprints.ExplorationModule

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
    with CanIdentify {

  def run(): Unit =
    val sortedPaths   = inputModelsPaths.sortBy(_.toString())
    val messageDigest = MessageDigest.getInstance("SHA-1")
    messageDigest.reset()
    val digested = messageDigest.digest(sortedPaths.flatMap(_.toString().map(_.toByte)).toArray)
    val stringOfDigested = Base64.getEncoder().encodeToString(digested)
    val runPath          = os.pwd / "run" / stringOfDigested
    logger.info(s"Run folder: ${runPath.toString()}")
    val inputsPath         = runPath / "inputs"
    val inputsPathFiodl    = inputsPath / "fiodl"
    val inputsPathJson     = inputsPath / "json"
    val exploredPath       = runPath / "explored"
    val exploredPathJson   = exploredPath / "json"
    val identifiedPath     = runPath / "identified"
    val identifiedPathJson = identifiedPath / "json"
    val explorablePath     = runPath / "explorable"
    val explorablePathJson = explorablePath / "json"
    val outputsPath        = runPath / "outputs"
    val outputsPathJson    = outputsPath / "json"
    val outputsPathFiodl   = outputsPath / "fiodl"
    os.makeDir.all(inputsPath)
    os.makeDir.all(inputsPathJson)
    os.makeDir.all(exploredPath)
    os.makeDir.all(exploredPathJson)
    os.makeDir.all(identifiedPath)
    os.makeDir.all(identifiedPathJson)
    os.makeDir.all(explorablePath)
    os.makeDir.all(explorablePathJson)
    os.makeDir.all(outputsPath)
    os.makeDir.all(outputsPathJson)
    os.makeDir.all(outputsPathFiodl)
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
      val header = model.header.copy(model_paths =
        validForSyDeInputs.filter((_, b) => b).map((p, _) => p.toString()).toSet
      )
      os.write.over(inputsPathJson / "header_ForSyDeDesignModel.json", header.asText)
      val identified = identifyDecisionModels(Set(model), identificationModules)
      logger.info(s"Identification finished with ${identified.size} decision model(s).")
      if (identified.size > 0)
        // save the identified models
        for ((dm, i) <- identified.zipWithIndex) {
          saveDecisionModel(identifiedPathJson, dm, i)
        }
        // now continue with flow
        val chosen = chooseExplorersAndModels(identified, explorationModules.map(_.asInstanceOf[ExplorationLibrary]))
        val chosenFiltered =
          if (allowedDecisionModels.size > 0) then
            chosen.filter(combo =>
              allowedDecisionModels.contains(combo.decisionModel.uniqueIdentifier)
            )
          else chosen
        logger.info(
          s"Total of ${chosenFiltered.size} combo of decision model(s) and explorer(s) chosen."
        )
        for ((combo, i) <- chosen.zipWithIndex) {
          saveCombo(explorablePathJson, combo, i)
        }
        if (chosenFiltered.size > 1) {
          logger.warn(s"Taking a random decision model and explorer combo.")
        }
        val numSols = chosenFiltered.headOption
          .map(combo =>
            combo.explorer
              .explore(combo.decisionModel, explorationTimeOutInSecs)
              .zipWithIndex
              .map((decisionModel, num) => {
                saveDecisionModel(exploredPathJson, decisionModel, num)
                (decisionModel, num)
              })
              .flatMap((m, res) =>
                integrateDecisionModel(model, m, identificationModules).map((_, res))
              )
              .map((m, res) =>
                m match {
                  case fdm @ ForSyDeDesignModel(m) =>
                    val oPath = outputsPathFiodl / s"integrated_0_${res}_ForSyDeDesignModel.fiodl"
                    modelHandler.writeModel(model.systemGraph.merge(m), oPath.toNIO)
                    val oHeader = fdm.header.copy(model_paths = Set(oPath.toString))
                    os.write.over(
                      outputsPathJson / s"header_0_${res}_ForSyDeDesignModel.json",
                      oHeader.asText
                    )
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

  protected def saveDecisionModel(
      p: os.Path,
      m: DecisionModel,
      num_prefix: Int = 0
  ): DecisionModelHeader = {
    m match {
      case complete: CompleteDecisionModel =>
        val bodyPath    = p / s"body_0_${num_prefix}_${complete.uniqueIdentifier}.json"
        val headerExtra = m.header.copy(body_path = Some(bodyPath.toString))
        os.write.over(bodyPath, complete.bodyAsText)
        os.write.over(
          p / s"header_0_${num_prefix}_${complete.uniqueIdentifier}.json",
          headerExtra.asText
        )
        headerExtra
      case _ =>
        os.write.over(p / s"header_0_${num_prefix}_${m.uniqueIdentifier}.json", m.header.asText)
        m.header
    }
  }

  protected def saveCombo(
      p: os.Path,
      m: ExplorationCombination,
      num_prefix: Int = 0
  ): ExplorationCombinationHeader = {
    m.decisionModel match {
      case complete: CompleteDecisionModel =>
        val savedDecisionModelHeader = saveDecisionModel(p, complete, num_prefix)
        val comboExtraHeader = m.header.copy(decision_model_header = savedDecisionModelHeader)
        os.write.over(
          p / s"combination_0_${num_prefix}_${m.explorer.uniqueIdentifier}_${m.decisionModel.uniqueIdentifier}.json",
          comboExtraHeader.asText
        )
        comboExtraHeader
      case _ =>
        os.write.over(
          p / s"combination_0_${num_prefix}_${m.explorer.uniqueIdentifier}_${m.decisionModel.uniqueIdentifier}.json",
          m.header.asText
        )
        m.header
    }
  }

}
