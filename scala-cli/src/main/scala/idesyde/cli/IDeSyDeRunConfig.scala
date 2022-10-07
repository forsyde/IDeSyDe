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
import idesyde.identification.forsyde.api.ForSyDeIdentificationModule
import idesyde.identification.minizinc.api.MinizincIdentificationModule
import idesyde.identification.DecisionModel

case class IDeSyDeRunConfig(
    var inputModelsPaths: Buffer[Path] = Buffer.empty,
    var outputModelPath: Path = Paths.get("idesyde-result.forsyde.xml"),
    var allowedDecisionModels: Buffer[String] = Buffer(),
    var verbosityLevel: String = "INFO",
    var solutionLimiter: Int = 0,
    val debugLogger: (String) => Unit = (s) => {},
    val infoLogger: (String) => Unit = (s) => {},
    val warnLogger: (String) => Unit = (s) => {},
    val errorLogger: (String) => Unit = (s) => {},
    executionContext: ExecutionContext
) {

  val explorationHandler = ExplorationHandler(infoLogger = infoLogger, debugLogger = debugLogger)
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler(infoLogger = infoLogger, debugLogger = debugLogger)
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  def run(): Unit =
    val modelHandler = ForSyDeModelHandler()
    val validInputs =
      inputModelsPaths.map(f => (f, modelHandler.canLoadModel(f)))
    if (validInputs.forall((p, b) => !b)) {
      errorLogger(
        "At least one valid model is necessary"
      )
    } else if (validInputs.exists((p, b) => !b)) {
      errorLogger("These inputs are invalid (unknown format): " + validInputs.filter((p, b) => b).map((p, b) => p.getFileName()).mkString(", "))
    } else {
      infoLogger("Reading and merging input models.")
      val model = validInputs
        .map((p, _) => modelHandler.loadModel(p))
        .foldLeft(ForSyDeSystemGraph())((merged, m) =>
          merged.mergeInPlace(m)
          merged
        )

      val identified = identificationHandler.identifyDecisionModels(model)
      infoLogger(s"Identification finished with ${identified.size} decision model(s).")
      if (identified.size > 0)
        val chosen = explorationHandler.chooseExplorersAndModels(identified)
        val chosenFiltered =
          if (allowedDecisionModels.size > 0) then
            chosen.filter((exp, dm) => allowedDecisionModels.contains(dm.uniqueIdentifier))
          else chosen
        infoLogger(s"Total of ${chosenFiltered.size} combo of decision model(s) and explorer(s) chosen.")
        // identified.foreach(m => m match {
        //   case mzn: MiniZincForSyDeDecisionModel => scribe.debug(s"mzn model: ${mzn.mznInputs.toString}")
        // })
        if (chosenFiltered.size > 1) {
          warnLogger(s"Taking a random decision model and explorer combo.")
        }
        val numSols = chosenFiltered.headOption
          .filter((e, decisionModel) =>
            e.isInstanceOf[ForSyDeIOExplorer] && decisionModel.isInstanceOf[ForSyDeDecisionModel]
          )
          .map((e, m) => (e.asInstanceOf[ForSyDeIOExplorer], m.asInstanceOf[ForSyDeDecisionModel]))
          .map((explorer, decisionModel) =>
            explorer
              .explore(decisionModel)(using executionContext)
              .scanLeft(0)((res, result) => {
                if (!outputModelPath.toFile.exists || outputModelPath.toFile.isFile) then
                  debugLogger(s"writing solution at ${outputModelPath.toString}")
                  modelHandler.writeModel(model.merge(result), outputModelPath)
                else if (outputModelPath.toFile.exists && outputModelPath.toFile.isDirectory) then
                  val outPath = outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                  debugLogger(s"writing solution at ${outPath.toString}")
                  modelHandler.writeModel(
                    model.merge(result),
                    outputModelPath.resolve(Paths.get(s"solution_${res.toString}.fiodl"))
                  )
                res + 1
              }).takeWhile(res => (solutionLimiter <= 0) || (solutionLimiter > 0 && res <= solutionLimiter)).last
          )
          .getOrElse(0)
        if (numSols > 0)
          infoLogger(s"Finished exploration with ${numSols} solution(s)")
        else
          infoLogger(s"Finished exploration with no solution")
      //scribe.info("Finished successfully")
    }

}
