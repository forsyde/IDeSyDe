package workload

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.api.ExplorationHandler
import idesyde.identification.api.IdentificationHandler
import idesyde.exploration.ChocoExplorationModule
import idesyde.identification.api.ChocoIdentificationModule
import idesyde.identification.api.ForSyDeIdentificationModule
import idesyde.identification.api.MinizincIdentificationModule
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.drivers.ForSyDeAmaltheaDriver
import java.nio.file.Paths

class PanoramaUseCaseWithoutSolutionSuite extends AnyFunSuite {

  val explorationHandler = ExplorationHandler()
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler()
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

    val forSyDeModelHandler = ForSyDeModelHandler().registerDriver(ForSyDeAmaltheaDriver())

    val flightInfo = forSyDeModelHandler.loadModel(Paths.get("tests/models/panorama/flight-information-system_2.0.0.amxmi"))
    val radar = forSyDeModelHandler.loadModel(Paths.get("tests/models/panorama/radar-system-easier_2.0.0.amxmi"))
    val bounds = forSyDeModelHandler.loadModel(Paths.get("tests/models/panorama/utilizationBounds.forsyde.xmi"))
    val model = flightInfo.merge(radar).merge(bounds)
    lazy val identified = identificationHandler.identifyDecisionModels(model)
    lazy val chosen = explorationHandler.chooseExplorersAndModels(identified)

    test("PANORAMA case study without any solutions - At least 1 decision model") {        
        assert(identified.size > 0)
    }

    test("PANORAMA case study without any solutions - At least 1 combo") {        
        assert(chosen.size > 0)
    }

}
