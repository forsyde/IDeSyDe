package sdf

import mixins.{LoggingMixin, PlatformExperimentCreator}
import org.scalatest.funsuite.AnyFunSuite
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.utils.SimpleStandardIOLogger
import idesyde.utils.Logger

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

}
