package sdf

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
import java.nio.file.Files
import java.nio.file.Paths

class RosvallSander2014Tests extends AnyFunSuite with LoggingMixin with PlatformExperimentCreator {

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

  val sobelSDF3    = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/a_sobel.hsdf.xml")
  val susanSDF3    = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/b_susan.hsdf.xml")
  val rastaSDF3    = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/c_rasta.hsdf.xml")
  val jpegEnc1SDF3 = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/d_jpegEnc1.hsdf.xml")
  val g10_3_cyclicSDF3 = forSyDeModelHandler.loadModel("scala-tests/models/sdf3/g10_3_cycl.sdf.xml")

  val small8NodeBusPlatform = makeTDMASingleBusPlatform(8, 128L)

  val rasta_and_jpeg_case = rastaSDF3.merge(jpegEnc1SDF3).merge(small8NodeBusPlatform)

  test("Write to disk the applications combined") {
    Files.createDirectories(Paths.get("scala-tests/models/forsyde_sdf"))
    forSyDeModelHandler.writeModel(sobelSDF3.merge(susanSDF3).merge(rastaSDF3).merge(jpegEnc1SDF3).merge(g10_3_cyclicSDF3), "scala-tests/models/forsyde_sdf/combined.fiodl")
  }

  test("Find a solution to the first case of Experiment III") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(rasta_and_jpeg_case)))
    assert(identified.size > 0)
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    val solList = chosen.headOption
      .map((e, m) => {
        e.explore(m)
      })
      .getOrElse(LazyList.empty)
      .take(3)
    assert(solList.size > 1)
  }

}
