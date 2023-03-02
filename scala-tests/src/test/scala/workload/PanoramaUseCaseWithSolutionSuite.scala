package workload

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.ExplorationHandler
import idesyde.identification.IdentificationHandler
import idesyde.exploration.ChocoExplorationModule
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.identification.minizinc.MinizincIdentificationModule
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.amalthea.drivers.ForSyDeAmaltheaDriver
import java.nio.file.Paths
import scribe.Level
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.identification.forsyde.ForSyDeDesignModel
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext

import mixins.LoggingMixin
import forsyde.io.java.kgt.drivers.ForSyDeKGTDriver
import idesyde.utils.Logger
import idesyde.utils.SimpleStandardIOLogger

class PanoramaUseCaseWithSolutionSuite extends AnyFunSuite with LoggingMixin {

  given ExecutionContext = ExecutionContext.global

  setNormal()

  given Logger = SimpleStandardIOLogger

  val explorationHandler = ExplorationHandler()
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler()
    .registerIdentificationRule(CommonIdentificationModule())
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  val forSyDeModelHandler =
    ForSyDeModelHandler().registerDriver(ForSyDeAmaltheaDriver()).registerDriver(ForSyDeKGTDriver())

  val flightInfo = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/flight-information-system-easier.amxmi")
  )
  val radar = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/radar-system-easier.amxmi")
  )
  val bounds =
    forSyDeModelHandler.loadModel(
      Paths.get("scala-tests/models/panorama/utilizationBounds.forsyde.xmi")
    )
  val model      = ForSyDeDesignModel(flightInfo.merge(radar).merge(bounds))
  val identified = identificationHandler.identifyDecisionModels(Set(model))
  val chosen     = explorationHandler.chooseExplorersAndModels(identified)

  test("PANORAMA case study - can write back model before solution") {
    forSyDeModelHandler.writeModel(
      model.systemGraph,
      "scala-tests/models/panorama/input_to_dse.fiodl"
    )
    forSyDeModelHandler.writeModel(
      model.systemGraph,
      "scala-tests/models/panorama/input_to_dse.amxmi"
    )
    forSyDeModelHandler.writeModel(
      model.systemGraph,
      "scala-tests/models/panorama/input_to_dse_visual.kgt"
    )
  }

  test("PANORAMA case study with any solutions - At least 1 decision model and combo") {
    assert(identified.size > 0)
    assert(chosen.size > 0)
  }

  test("PANORAMA case study with any solutions - at least 1 solution found") {
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .take(1)
          .flatMap(decisionModel =>
            identificationHandler.integrateDecisionModel(model, decisionModel)
          )
          .flatMap(designModel =>
            designModel match {
              case f: ForSyDeDesignModel => Some(f.systemGraph); case _ => Option.empty
            }
          )
          .map(sol =>
            forSyDeModelHandler
              .writeModel(sol, "scala-tests/models/panorama/output_of_dse.fiodl")
            forSyDeModelHandler
              .writeModel(sol, "scala-tests/models/panorama/output_of_dse_visual.kgt")
            sol
          )
      )
      .take(1)
    assert(solutions.size > 0)
  }

}
