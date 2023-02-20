package mixins

import scala.jdk.CollectionConverters._

import forsyde.io.java.core.{EdgeTrait, ForSyDeSystemGraph}
import forsyde.io.java.typed.viewers.decision.Allocated
import forsyde.io.java.typed.viewers.platform.{AbstractStructure, GenericMemoryModule, InstrumentedCommunicationModule, InstrumentedProcessingModule}
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import forsyde.io.java.typed.viewers.visualization.{GreyBox, Visualizable}

trait PlatformExperimentCreator {

  def makeTDMASingleBusPlatform(nCores: Int = 8, flitSize: Long = 32L): ForSyDeSystemGraph = {
    val m = new ForSyDeSystemGraph()
    var niMesh = Array.fill[InstrumentedCommunicationModule](nCores)(null)
    // put the microblaze elements
    for (i <- 0 until nCores) {
      val tile = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_" + i)))
      val mem =
        GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_mem" + i)))
      val ni = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("micro_blaze_ni" + i))
      )
      val scheduler = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_os" + i))
      val niSchedule = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_ni_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(1048576L * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(flitSize)
      ni.setMaxConcurrentFlits(nCores)
      ni.setMaxCyclesPerFlit(nCores)
      ni.setInitialLatency(0L)
      proc.setModalInstructionsPerCycle(
        Map(
          "eco" -> Map(
            "all" -> (1.0 / 65.0).asInstanceOf[java.lang.Double]
          ).asJava,
          "default" -> Map(
            "all" -> (1.0 / 13.0).asInstanceOf[java.lang.Double]
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
    val bus = InstrumentedCommunicationModule.enforce(m.newVertex("TDMBus"))
    val busSched = StaticCyclicScheduler.enforce(m.newVertex("busSched"))
    bus.setOperatingFrequencyInHertz(666667000L)
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

  def makeMeshNoCPlatform(x: Int, y: Int, flitSize: Long = 32L, virtualChannels: Int = 6): ForSyDeSystemGraph = {
    val nCores = x * y
    val m = ForSyDeSystemGraph()
    var niMesh = Array.fill(y)(Array.fill[InstrumentedCommunicationModule](x)(null))
    // put the proc elements
    for (i <- 0 until nCores; col = i % x; row = (i - col) / x) {
      val tile = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("tile_cpu_" + i)))
      val mem = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("tile_mem" + i)))
      val ni =
        InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("tile_ni" + i)))
      val router =
        InstrumentedCommunicationModule.enforce(
          Visualizable.enforce(m.newVertex("tile_router" + i))
        )
      val scheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_os" + i))
      val niScheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_ni_slots" + i))
      val routerScheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_router_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(16000 * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(flitSize)
      ni.setMaxConcurrentFlits(virtualChannels)
      ni.setMaxCyclesPerFlit(virtualChannels)
      ni.setInitialLatency(0L)
      router.setOperatingFrequencyInHertz(50000000L)
      router.setFlitSizeInBits(flitSize)
      router.setMaxConcurrentFlits(virtualChannels)
      router.setMaxCyclesPerFlit(virtualChannels)
      router.setInitialLatency(0L)
      proc.setModalInstructionsPerCycle(
        Map(
          "eco" -> Map(
            "all" -> (1.0 / 1.2).asInstanceOf[java.lang.Double]
          ).asJava,
          "default" -> Map(
            "all" -> (1.0).asInstanceOf[java.lang.Double]
          ).asJava
        ).asJava
      )
      proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
      mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
      ni.getViewedVertex().addPorts("tileMemory", "tileProcessor", "router")
      router.getViewedVertex().addPorts("ni")
      tile.insertSubmodulesPort(m, proc)
      tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
      tile.insertSubmodulesPort(m, mem)
      tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
      tile.insertSubmodulesPort(m, ni)
      tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
      GreyBox.enforce(proc).insertContainedPort(m, Visualizable.enforce(scheduler))
      Allocated.enforce(scheduler).insertAllocationHostsPort(m, proc)
      Allocated.enforce(niScheduler).insertAllocationHostsPort(m, ni)
      Allocated.enforce(routerScheduler).insertAllocationHostsPort(m, router)
      // connect them
      niMesh(row)(col) = router
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
      m.connect(
        router,
        ni,
        "ni",
        "router",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        router,
        "router",
        "ni",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )

    }

    // and now we connect the NIs in the mesh
    for (i <- 0 until nCores; col = i % x; row = (i - col) / x) {
      if (row > 0) {
        val r = row - 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
        niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(r)(col),
          s"to_${r}_${col}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(r)(col),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${r}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (row < 4) {
        val r = row + 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
        niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(r)(col),
          s"to_${r}_${col}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(r)(col),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${r}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (col > 0) {
        val c = col - 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
        niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(row)(c),
          s"to_${row}_${c}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(row)(c),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${c}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (col < 5) {
        val c = col + 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
        niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(row)(c),
          s"to_${row}_${c}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(row)(c),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${c}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
    }
    m
  }
}
