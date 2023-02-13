package sdf
import forsyde.io.java.amalthea.drivers.ForSyDeAmaltheaDriver
import java.nio.file.Paths
import org.scalatest.funsuite.AnyFunSuite
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.graphviz.drivers.ForSyDeGraphVizDriver
import forsyde.io.java.kgt.drivers.ForSyDeKGTDriver
import forsyde.io.java.sdf3.drivers.ForSyDeSDF3Driver
import idesyde.utils.SimpleStandardIOLogger
import idesyde.utils.Logger
import idesyde.exploration.ExplorationHandler
import idesyde.identification.IdentificationHandler
import mixins.LoggingMixin
import mixins.PlatformExperimentCreator
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.{ForSyDeDesignModel, ForSyDeIdentificationModule}
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import tags.ResourceHungry

class TaskandSDFServerTest extends AnyFunSuite with LoggingMixin with PlatformExperimentCreator {

  given Logger = SimpleStandardIOLogger

  val explorationHandler = ExplorationHandler(
  ).registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler(
  ).registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())
    .registerIdentificationRule(CommonIdentificationModule())
    .registerIdentificationRule(ChocoIdentificationModule())

  val forSyDeModelHandler = ForSyDeModelHandler()
    .registerDriver(ForSyDeSDF3Driver())
    .registerDriver(ForSyDeKGTDriver())
    .registerDriver(ForSyDeGraphVizDriver())
    .registerDriver(ForSyDeAmaltheaDriver())

  val sobelSDF3    = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/a_sobel.hsdf.xml")
  val susanSDF3    = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/b_susan.hsdf.xml")
  val rastaSDF3    = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/c_rasta.hsdf.xml")
  val jpegEnc1SDF3 = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/d_jpegEnc1.hsdf.xml")
  val g10_3_cyclicSDF3 = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/g10_3_cycl.sdf.xml")
  val flightInfo = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/flight-information-system.amxmi")
  )
  val radar = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/radar-system.amxmi")
  )
  val bounds =
    forSyDeModelHandler.loadModel(Paths.get("scala-tests/models/panorama/utilizationBounds.forsyde.xmi"))
  val small8NodeBusPlatform = makeTDMASingleBusPlatform(8, 128L)

  val rasta_and_jpeg_case = flightInfo.merge(radar).merge(bounds).merge(rastaSDF3).merge(jpegEnc1SDF3).merge(small8NodeBusPlatform)
/*test("PANORAMA case study without any solutions - no solution found", ResourceHungry) {
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .take(1)
          .flatMap(decisionModel => identificationHandler.integrateDecisionModel(model, decisionModel))
          .flatMap(designModel => designModel match {case f: ForSyDeDesignModel => Some(f.systemGraph); case _ => Option.empty})
          .map(sol =>
            forSyDeModelHandler
              .writeModel(model.systemGraph, "scala-tests/models/panorama/wrong_output_of_dse.fiodl")
            sol
          )
      )
      .take(1)
    assert(solutions.size == 0)
  }*/
  test("Find a ") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(rasta_and_jpeg_case)))
    assert(identified.size > 0)
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    /*val solList = chosen.headOption
      .map((e, m) => {
        e.explore(m)
      })
      .getOrElse(LazyList.empty)
      .take(3)
    assert(solList.size > 1)*/
  }

}
