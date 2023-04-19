package workload

import org.scalatest.funsuite.AnyFunSuite
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.amalthea.drivers.ForSyDeAmaltheaDriver
import java.nio.file.Paths
import scribe.Level
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext

import mixins.LoggingMixin
import mixins.HasShortcuts
import idesyde.utils.Logger
import idesyde.utils.SimpleStandardIOLogger
import tags.ResourceHungry
import idesyde.forsydeio.ForSyDeDesignModel

class PanoramaUseCaseWithoutSolutionSuite extends AnyFunSuite with LoggingMixin with HasShortcuts {

  given ExecutionContext = ExecutionContext.global

  setNormal()

  val forSyDeModelHandler = ForSyDeModelHandler().registerDriver(ForSyDeAmaltheaDriver())

  val flightInfo = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/flight-information-system.amxmi")
  )
  val radar = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/radar-system.amxmi")
  )
  val bounds =
    forSyDeModelHandler.loadModel(
      Paths.get("scala-tests/models/panorama/utilizationBounds.forsyde.xmi")
    )
  val model           = ForSyDeDesignModel(flightInfo.merge(radar).merge(bounds))
  lazy val identified = identify(Set(model))
  lazy val chosen     = getExplorerAndModel(identified)

  test("PANORAMA case study without any solutions - At least 1 decision model") {
    assert(identified.size > 0)
  }

  test("PANORAMA case study without any solutions - At least 1 combo") {
    assert(chosen.size > 0)
  }

  test("PANORAMA case study without any solutions - no solution found", ResourceHungry) {
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .take(1)
          .flatMap(decisionModel => integrate(model, decisionModel))
          .flatMap(designModel =>
            designModel match {
              case f: ForSyDeDesignModel => Some(f.systemGraph); case _ => Option.empty
            }
          )
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                model.systemGraph,
                "scala-tests/models/panorama/wrong_output_of_dse.fiodl"
              )
            sol
          )
      )
      .take(1)
    assert(solutions.size == 0)
  }

}
