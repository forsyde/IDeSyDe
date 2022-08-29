package sdf

import scala.jdk.CollectionConverters.*

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.ExplorationHandler
import idesyde.identification.IdentificationHandler
import forsyde.io.java.drivers.ForSyDeModelHandler
import scala.concurrent.ExecutionContext
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.api.ForSyDeIdentificationModule
import idesyde.identification.minizinc.api.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.core.EdgeTrait
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.AbstractStructure
import forsyde.io.java.typed.viewers.visualization.GreyBox
import idesyde.identification.forsyde.models.platform.TiledDigitalHardware
import idesyde.identification.forsyde.models.sdf.SDFApplication
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import forsyde.io.java.typed.viewers.decision.Allocated
import idesyde.identification.forsyde.models.platform.SchedulableTiledDigitalHardware
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW

import mixins.LoggingMixin
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW
import forsyde.io.java.graphviz.drivers.ForSyDeGraphVizDriver
import forsyde.io.java.kgt.drivers.ForSyDeKGTDriver
import forsyde.io.java.sdf3.drivers.ForSyDeSDF3Driver
import java.nio.file.Files
import java.nio.file.Paths

/** This test suite uses as much as possible the experiments from the paper
  *
  * K. Rosvall, T. Mohammadat, G. Ungureanu, J. Öberg, and I. Sander, “Exploring Power and
  * Throughput for Dataflow Applications on Predictable NoC Multiprocessors,” Aug. 2018, pp.
  * 719–726. doi: 10.1109/DSD.2018.00011.
  *
  * Which are mostly (all?) present in the DeSyDe source code repository
  * https://github.com/forsyde/DeSyDe, in its examples folder.
  */
class SDFOnTileNoCUseCaseWithSolution extends AnyFunSuite with LoggingMixin {

  given ExecutionContext = ExecutionContext.global

  setNormal()

  Files.createDirectories(Paths.get("tests/models/sdf3/results"))

  val explorationHandler = ExplorationHandler(
    infoLogger = (s: String) => scribe.info(s),
    debugLogger = (s: String) => scribe.debug(s)
  )
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler(
    infoLogger = (s: String) => scribe.info(s),
    debugLogger = (s: String) => scribe.debug(s)
  )
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  val forSyDeModelHandler = ForSyDeModelHandler()
    .registerDriver(ForSyDeSDF3Driver())
    .registerDriver(ForSyDeKGTDriver())
    .registerDriver(ForSyDeGraphVizDriver())

  // the platform is done here in memory since the format used by the DeSyDe tool is non-standard,
  // even for the SDF3 group.
  val small2x2PlatformModel = {
    val m      = ForSyDeSystemGraph()
    var niMesh = Array.fill(2)(Array.fill[InstrumentedCommunicationModule](2)(null))
    // put the microblaze elements
    for (i <- 0 until 3; row = i % 2; col = (i - row) / 2) {
      val tile     = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_" + i)))
      val mem =
        GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_mem" + i)))
      val ni = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("micro_blaze_ni" + i))
      )
      val router = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("router" + i))
      )
      val scheduler = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_os" + i))
      val niSchedule = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_ni_slots" + i))
      val routerSchedule = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_router_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(1048576L * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(128L)
      ni.setMaxConcurrentFlits(4)
      ni.setMaxCyclesPerFlit(4)
      ni.setInitialLatency(0L)
      router.setOperatingFrequencyInHertz(50000000L)
      router.setFlitSizeInBits(128L)
      router.setMaxConcurrentFlits(4)
      router.setMaxCyclesPerFlit(4)
      router.setInitialLatency(0L)
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
      ni.getViewedVertex().addPorts("tileMemory", "tileProcessor", "router")
      router.getViewedVertex().addPorts("tileNI")
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
        "tileNI",
        "router",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        router,
        "router",
        "tileNI",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      GreyBox.enforce(proc).insertContainedPort(m, Visualizable.enforce(scheduler))
      Allocated.enforce(scheduler).insertAllocationHostsPort(m, proc)
      Allocated.enforce(niSchedule).insertAllocationHostsPort(m, ni)
      Allocated.enforce(routerSchedule).insertAllocationHostsPort(m, router)
    }
    // and now the Arm tile
    val armTile = AbstractStructure.enforce(m.newVertex("arm_tile"))
    val armProc = InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("arm_cpu")))
    val armMem  = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("arm_mem")))
    val armNi = InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("arm_ni")))
    val armScheduler = StaticCyclicScheduler.enforce(m.newVertex("arm_os"))
    val armRouter =
      InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("arm_router")))
    val armNiScheduler = StaticCyclicScheduler.enforce(m.newVertex("arm_ni_slots"))
    val armRouterScheduler = StaticCyclicScheduler.enforce(m.newVertex("arm_router_slots"))
    armProc.setOperatingFrequencyInHertz(666667000L)
    armMem.setOperatingFrequencyInHertz(666667000L)
    armMem.setSpaceInBits(4294967296L * 8L)
    armNi.setOperatingFrequencyInHertz(666667000L)
    armNi.setFlitSizeInBits(128L)
    armNi.setMaxConcurrentFlits(4)
    armNi.setMaxCyclesPerFlit(4)
    armNi.setInitialLatency(0L)
    armRouter.setOperatingFrequencyInHertz(666667000L)
    armRouter.setFlitSizeInBits(128L)
    armRouter.setMaxConcurrentFlits(4)
    armRouter.setMaxCyclesPerFlit(4)
    armRouter.setInitialLatency(0L)
    armProc.setModalInstructionsPerCycle(
      Map(
        "eco" -> Map(
          "all" -> (1.0 / 10.0).asInstanceOf[java.lang.Double]
        ).asJava,
        "default" -> Map(
          "all" -> (1.0).asInstanceOf[java.lang.Double]
        ).asJava
      ).asJava
    )
    // connect them
    val armTileVisu = GreyBox.enforce(armTile)
    armTile.insertSubmodulesPort(m, armProc)
    armTileVisu.insertContainedPort(m, Visualizable.enforce(armProc))
    armTile.insertSubmodulesPort(m, armMem)
    armTileVisu.insertContainedPort(m, Visualizable.enforce(armMem))
    armTile.insertSubmodulesPort(m, armNi)
    armTileVisu.insertContainedPort(m, Visualizable.enforce(armNi))
    armProc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
    armMem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
    armNi.getViewedVertex().addPorts("tileMemory", "tileProcessor", "router")
    armRouter.getViewedVertex().addPorts("tileNI")
    niMesh(1)(1) = armRouter
    m.connect(
      armProc,
      armNi,
      "networkInterface",
      "tileProcessor",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armNi,
      armProc,
      "tileProcessor",
      "networkInterface",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armProc,
      armMem,
      "defaultMemory",
      "instructionsAndData",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armMem,
      armProc,
      "instructionsAndData",
      "defaultMemory",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armMem,
      armNi,
      "networkInterface",
      "tileMemory",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armNi,
      armMem,
      "tileMemory",
      "networkInterface",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armRouter,
      armNi,
      "tileNI",
      "router",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armNi,
      armRouter,
      "router",
      "tileNI",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    GreyBox.enforce(armProc).insertContainedPort(m, Visualizable.enforce(armScheduler))
    Allocated.enforce(armScheduler).insertAllocationHostsPort(m, armProc)
    Allocated.enforce(armNiScheduler).insertAllocationHostsPort(m, armNi)
    Allocated.enforce(armRouterScheduler).insertAllocationHostsPort(m, armRouter)
    // and now we connect the NIs in the mesh
    for (i <- 0 until 4; row = i % 2; col = (i - row) / 2) {
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
      if (row < 1) {
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
      if (col < 1) {
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

  // and now we construct the bigger in memory for the same reason
  val large5x6PlatformModel = {
    val m      = ForSyDeSystemGraph()
    var niMesh = Array.fill(5)(Array.fill[InstrumentedCommunicationModule](6)(null))
    // put the proc elements
    for (i <- 0 until 30; row = i % 5; col = (i - row) / 5) {
      val tile     = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("tile_cpu_" + i)))
      val mem = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("tile_mem" + i)))
      val ni =
        InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("tile_ni" + i)))
      val router = 
        InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("tile_router" + i)))
      val scheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_os" + i))
      val niScheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_ni_slots" + i))
      val routerScheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_router_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(16000 * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(128L)
      ni.setMaxConcurrentFlits(4)
      ni.setMaxCyclesPerFlit(6)
      ni.setInitialLatency(0L)
      router.setOperatingFrequencyInHertz(50000000L)
      router.setFlitSizeInBits(128L)
      router.setMaxConcurrentFlits(4)
      router.setMaxCyclesPerFlit(6)
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
    for (i <- 0 until 30; row = i % 5; col = (i - row) / 5) {
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

  val sobelSDF3        = forSyDeModelHandler.loadModel("tests/models/sdf3/a_sobel.hsdf.xml")
  val susanSDF3        = forSyDeModelHandler.loadModel("tests/models/sdf3/b_susan.hsdf.xml")
  val rastaSDF3        = forSyDeModelHandler.loadModel("tests/models/sdf3/c_rasta.hsdf.xml")
  val jpegEnc1SDF3     = forSyDeModelHandler.loadModel("tests/models/sdf3/d_jpegEnc1.hsdf.xml")
  val g10_3_cyclicSDF3 = forSyDeModelHandler.loadModel("tests/models/sdf3/g10_3_cycl.sdf.xml")
  val allSDFApps =
    sobelSDF3.merge(susanSDF3).merge(rastaSDF3).merge(jpegEnc1SDF3).merge(g10_3_cyclicSDF3)

  val appsAndSmall = allSDFApps.merge(small2x2PlatformModel)
  val appsAndLarge = allSDFApps.merge(large5x6PlatformModel)

  test("Created platform models in memory successfully and can write them out") {
    forSyDeModelHandler.writeModel(small2x2PlatformModel, "tests/models/small_platform.fiodl")
    forSyDeModelHandler.writeModel(large5x6PlatformModel, "tests/models/large_platform.fiodl")
    forSyDeModelHandler.writeModel(small2x2PlatformModel, "tests/models/small_platform_visual.kgt")
    forSyDeModelHandler.writeModel(large5x6PlatformModel, "tests/models/large_platform_visual.kgt")
  }

  test("Correct decision model identification of the Small platform") {
    val identified = identificationHandler.identifyDecisionModels(small2x2PlatformModel)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SchedulableTiledDigitalHardware]).isDefined)
  }

  test("Correct decision model identification of the Large platform") {
    val identified = identificationHandler.identifyDecisionModels(large5x6PlatformModel)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SchedulableTiledDigitalHardware]).isDefined)
  }

  test("Correct decision model identification of Sobel") {
    val identified = identificationHandler.identifyDecisionModels(sobelSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val sobelDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(sobelDM.repetitionVectors.head.sameElements(Array(1, 1, 1, 1)))
    assert(sobelDM.sdfMaxParallelClusters.size == 3)
  }

  test("Correct identification and DSE of Sobel to Small") {
    val inputSystem = sobelSDF3.merge(small2x2PlatformModel)
    val identified  = identificationHandler.identifyDecisionModels(inputSystem)
    val chosen      = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    assert(chosen.find((_, m) => m.isInstanceOf[ChocoSDFToSChedTileHW]).isDefined)
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore[ForSyDeSystemGraph](decisionModel)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                inputSystem.merge(sol),
                "tests/models/sdf3/results/sobel_and_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              inputSystem.merge(sol),
              "tests/models/sdf3/results/sobel_and_small_result_visual.kgt"
            )
            sol
          )
      )
      .take(1)
    assert(solutions.size >= 1)
  }

  test("Correct identification and DSE of Sobel to Large") {
    val inputSystem = sobelSDF3.merge(large5x6PlatformModel)
    val identified  = identificationHandler.identifyDecisionModels(inputSystem)
    val chosen      = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    assert(chosen.find((_, m) => m.isInstanceOf[ChocoSDFToSChedTileHW]).isDefined)
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore[ForSyDeSystemGraph](decisionModel)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                inputSystem.merge(sol),
                "tests/models/sdf3/results/sobel_and_large_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              inputSystem.merge(sol),
              "tests/models/sdf3/results/sobel_and_large_result_visual.kgt"
            )
            sol
          )
      )
      .take(1)
    assert(solutions.size >= 1)
  }

  test("Correct decision model identification of SUSAN") {
    val identified = identificationHandler.identifyDecisionModels(susanSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val susanDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(susanDM.repetitionVectors.head.sameElements(Array(1, 1, 1, 1, 1)))
  }

  test("Correct decision model identification of RASTA") {
    val identified = identificationHandler.identifyDecisionModels(rastaSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val rastaDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(rastaDM.repetitionVectors.head.sameElements(Array(1, 1, 1, 1, 1, 1, 1)))
  }

  test("Correct decision model identification of JPEG") {
    val identified = identificationHandler.identifyDecisionModels(jpegEnc1SDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val jpegDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(jpegDM.repetitionVectors.head.sameElements(Array.fill(jpegDM.actors.size)(1)))
  }

  test("Correct decision model identification of Synthetic") {
    val identified = identificationHandler.identifyDecisionModels(g10_3_cyclicSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val syntheticDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(syntheticDM.repetitionVectors.head.sameElements(Array.fill(syntheticDM.actors.size)(1)))
  }

  test("Correct decision model identification of all Applications together") {
    val identified = identificationHandler.identifyDecisionModels(allSDFApps)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val allSDFAppsDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(
      allSDFAppsDM.repetitionVectors.head.sameElements(Array.fill(allSDFAppsDM.actors.size)(1))
    )
  }

  test("Correct identification and DSE of all and small platform") {
    val identified = identificationHandler.identifyDecisionModels(appsAndSmall)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFToSchedTiledHW]).isDefined)
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    assert(chosen.find((_, m) => m.isInstanceOf[ChocoSDFToSChedTileHW]).isDefined)
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore[ForSyDeSystemGraph](decisionModel)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                appsAndSmall.merge(sol),
                "tests/models/sdf3/results/all_and_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              appsAndSmall.merge(sol),
              "tests/models/sdf3/results/all_and_small_result_visual.kgt"
            )
            sol
          )
      )
      .take(1)
    assert(solutions.size >= 1)
  }

  test("Correct identification and DSE of all and large platform") {
    val identified = identificationHandler.identifyDecisionModels(appsAndLarge)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFToSchedTiledHW]).isDefined)
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    assert(chosen.find((_, m) => m.isInstanceOf[ChocoSDFToSChedTileHW]).isDefined)
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore[ForSyDeSystemGraph](decisionModel)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                appsAndLarge.merge(sol),
                "tests/models/sdf3/results/all_and_large_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              appsAndLarge.merge(sol),
              "tests/models/sdf3/results/all_and_large_result_visual.kgt"
            )
            sol
          )
      )
      .take(1)
    assert(solutions.size >= 1)
  }

}
