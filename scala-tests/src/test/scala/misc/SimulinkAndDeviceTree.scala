package misc

import upickle.default.*
import org.virtuslab.yaml.*

import mixins.LoggingMixin
import mixins.HasShortcuts
import org.scalatest.funsuite.AnyFunSuite
import idesyde.matlab.identification.SimulinkReactiveDesignModel
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.devicetree.identification.CanParseDeviceTree
import idesyde.devicetree.identification.DeviceTreeDesignModel
import idesyde.devicetree.OSDescription
import idesyde.devicetree.identification.OSDescriptionDesignModel
import idesyde.identification.choco.models.mixed.ChocoComDepTasksToMultiCore

class SimulinkAndDeviceTree
    extends AnyFunSuite
    with LoggingMixin
    with HasShortcuts
    with CanParseDeviceTree {

  val deviceTreeTests = devicetree.DeviceTreeParseTests()

  val simulinkTest1 = read[SimulinkReactiveDesignModel](
    os.read(os.pwd / "scala-tests" / "models" / "simulink" / "test1.json")
  )

  lazy val dt = parseDeviceTree(deviceTreeTests.deviceTreeinlinedCode) match {
    case Success(result, next) =>
      Some(DeviceTreeDesignModel(List(result)))
    case Failure(msg, next) => None
    case Error(msg, next)   => None
  }
  lazy val oses = deviceTreeTests.osYamlInlined.as[OSDescription] match {
    case Right(value) =>
      Some(OSDescriptionDesignModel(value))
    case Left(value) => None
  }
  lazy val identified = for (r <- dt; os <- oses) yield identify(Set(simulinkTest1, r, os))

  test("finds the DSE problem in the combined example") {
    for (iden <- identified) {
      assert(iden.exists(_.isInstanceOf[ChocoComDepTasksToMultiCore]))
    }
  }

}
