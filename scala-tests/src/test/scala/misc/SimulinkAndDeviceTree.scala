package misc

import upickle.default.*

import mixins.LoggingMixin
import mixins.HasShortcuts
import org.scalatest.funsuite.AnyFunSuite
import idesyde.matlab.identification.SimulinkReactiveDesignModel
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload

class SimulinkAndDeviceTree extends AnyFunSuite with LoggingMixin with HasShortcuts {

  val deviceTreeTests = devicetree.DeviceTreeParseTests()

  val simulinkTest1 = read[SimulinkReactiveDesignModel](
    os.read(os.pwd / "scala-tests" / "models" / "simulink" / "test1.json")
  )

  test("finds the comm. Tasks in one simulink example") {
    val identified = identify(simulinkTest1)
    assert(identified.exists(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload]))
  }

}
