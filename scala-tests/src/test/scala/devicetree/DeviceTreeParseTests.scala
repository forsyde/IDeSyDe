package devicetree

import org.scalatest.funsuite.AnyFunSuite
import mixins.LoggingMixin
import mixins.HasShortcuts
import idesyde.devicetree.identification.CanParseDeviceTree

class DeviceTreeParseTests
    extends AnyFunSuite
    with LoggingMixin
    with HasShortcuts
    with CanParseDeviceTree {

  val inlinedCode = """
    cpus {
      ublaze1: cpu@0 {
        timebase-frequency = <50000000>
      }
    }

    memory@0x00000 {
        
    }
    """

  test("Trying to parse the most basic ones") {
    val parser = parseDeviceTree(inlinedCode)
    println(parser.toString)
  }
}
