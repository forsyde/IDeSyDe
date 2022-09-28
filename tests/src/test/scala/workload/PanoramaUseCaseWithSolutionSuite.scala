package workload

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.ExplorationHandler
import idesyde.identification.IdentificationHandler
import idesyde.exploration.ChocoExplorationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.api.ForSyDeIdentificationModule
import idesyde.identification.minizinc.api.MinizincIdentificationModule
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.amalthea.drivers.ForSyDeAmaltheaDriver
import java.nio.file.Paths
import scribe.Level
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext

import mixins.LoggingMixin
import forsyde.io.java.kgt.drivers.ForSyDeKGTDriver

class PanoramaUseCaseWithSolutionSuite extends AnyFunSuite with LoggingMixin {

  given ExecutionContext = ExecutionContext.global

  setNormal()

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

  val forSyDeModelHandler = ForSyDeModelHandler().registerDriver(ForSyDeAmaltheaDriver()).registerDriver(ForSyDeKGTDriver())

  val flightInfo = forSyDeModelHandler.loadModel(
    Paths.get("tests/models/panorama/flight-information-system-easier.amxmi")
  )
  val radar = forSyDeModelHandler.loadModel(
    Paths.get("tests/models/panorama/radar-system-easier.amxmi")
  )
  val bounds =
    forSyDeModelHandler.loadModel(Paths.get("tests/models/panorama/utilizationBounds.forsyde.xmi"))
  val model           = flightInfo.merge(radar).merge(bounds)
  lazy val identified = identificationHandler.identifyDecisionModels(model)
  lazy val chosen     = explorationHandler.chooseExplorersAndModels(identified)

  test("PANORAMA case study - can write back model before solution") {
    forSyDeModelHandler.writeModel(model, "tests/models/panorama/input_to_dse.fiodl")
    forSyDeModelHandler.writeModel(model, "tests/models/panorama/input_to_dse.amxmi")
    forSyDeModelHandler.writeModel(model, "tests/models/panorama/input_to_dse_visual.kgt")
  }

  test("PANORAMA case study with any solutions - At least 1 decision model") {
    assert(identified.size > 0)
  }

  test("PANORAMA case study with any solutions - At least 1 combo") {
    assert(chosen.size > 0)
  }

  test("PANORAMA case study with any solutions - at least 1 solution found") {
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore[ForSyDeSystemGraph](decisionModel)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(model.merge(sol), "tests/models/panorama/output_of_dse.fiodl")
            forSyDeModelHandler
              .writeModel(model.merge(sol), "tests/models/panorama/output_of_dse_visual.kgt")
            sol
          ).take(1)
      ).take(1)
    assert(solutions.size > 0)
  }

}
