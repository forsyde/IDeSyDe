import scala.jdk.CollectionConverters._

import $ivy.`io.github.forsyde:forsyde-io-java-core:0.6.0`

import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.core.EdgeTrait
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.AbstractStructure
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import forsyde.io.java.typed.viewers.decision.Allocated

def makeTDMASingleBusPlatform(nCores: Int = 8, flitSize: Long = 32L, cpuFreq: Long = 50000000, cpuOpsPerSec: Double = 1.0 / 65.0): ForSyDeSystemGraph = {
    val m      = new ForSyDeSystemGraph()
    var niMesh = Array.fill[InstrumentedCommunicationModule](nCores)(null)
    // put the microblaze elements
    for (i <- 0 until nCores) {
      val tile     = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_" + i)))
      val mem =
        GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_mem" + i)))
      val ni = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("micro_blaze_ni" + i))
      )
      val scheduler  = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_os" + i))
      val niSchedule = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_ni_slots" + i))
      proc.setOperatingFrequencyInHertz(cpuFreq)
      mem.setOperatingFrequencyInHertz(cpuFreq)
      mem.setSpaceInBits(1048576L * 8L)
      ni.setOperatingFrequencyInHertz(cpuFreq)
      ni.setFlitSizeInBits(flitSize)
      ni.setMaxConcurrentFlits(nCores)
      ni.setMaxCyclesPerFlit(nCores)
      ni.setInitialLatency(0L)
      proc.setModalInstructionsPerCycle(
        Map(
          "default" -> Map(
            "all" -> cpuOpsPerSec.asInstanceOf[java.lang.Double]
          ).asJava
        ).asJava
      )
      // connect them
      tile.insertSubmodulesPort(m, proc)
      tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
      tile.insertSubmodulesPort(m, mem)
      tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
      tile.insertSubmodulesPort(m, ni)
      tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
      proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
      mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
      ni.getViewedVertex().addPorts("tileMemory", "tileProcessor", "bus")
      niMesh(i) = ni
      m.connect(
        proc,
        ni,
        "networkInterface",
        "tileProcessor",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        proc,
        "tileProcessor",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        proc,
        mem,
        "defaultMemory",
        "instructionsAndData",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        proc,
        "instructionsAndData",
        "defaultMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        ni,
        "networkInterface",
        "tileMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        mem,
        "tileMemory",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      GreyBox.enforce(proc).insertContainedPort(m, Visualizable.enforce(scheduler))
      Allocated.enforce(scheduler).insertAllocationHostsPort(m, proc)
      Allocated.enforce(niSchedule).insertAllocationHostsPort(m, ni)
    }
    // and now the bus
    val bus      = InstrumentedCommunicationModule.enforce(m.newVertex("TDMBus"))
    val busSched = StaticCyclicScheduler.enforce(m.newVertex("busSched"))
    bus.setOperatingFrequencyInHertz(cpuFreq)
    bus.setFlitSizeInBits(flitSize)
    bus.setMaxConcurrentFlits(nCores)
    bus.setMaxCyclesPerFlit(nCores)
    bus.setInitialLatency(0L)
    Allocated.enforce(busSched).insertAllocationHostsPort(m, bus)
    Visualizable.enforce(bus)
    // and now we connect the NIs in the mesh
    for (i <- 0 until nCores) {
      bus.getViewedVertex().addPort("ni_" + i)
      m.connect(
        niMesh(i),
        bus,
        "bus",
        "ni_" + i,
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        bus,
        niMesh(i),
        "ni_" + i,
        "bus",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
    }
    m
}

@main
def generator(cores: Int, outputDir: os.Path): Unit = {
  val platform = makeTDMASingleBusPlatform(cores)
  val handler = new ForSyDeModelHandler()
  handler.writeModel(platform, outputDir.toNIO)
}
