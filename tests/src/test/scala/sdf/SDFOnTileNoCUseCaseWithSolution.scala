package sdf

import scala.jdk.CollectionConverters.*

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.api.ExplorationHandler
import idesyde.identification.api.IdentificationHandler
import forsyde.io.java.drivers.ForSyDeModelHandler
import scala.concurrent.ExecutionContext
import idesyde.identification.api.ChocoIdentificationModule
import idesyde.identification.api.ForSyDeIdentificationModule
import idesyde.identification.api.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import forsyde.io.java.drivers.ForSyDeSDF3Driver
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.core.EdgeTrait
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.AbstractStructure
import forsyde.io.java.typed.viewers.visualization.GreyBox
import scribe.Level
import scribe.format.FormatterInterpolator
import scribe.format.FormatBlock
import idesyde.identification.models.platform.TiledDigitalHardware
import idesyde.identification.models.sdf.SDFApplication


/**
 * This test suite uses as much as possible the experiments from the paper
 * 
 * K. Rosvall, T. Mohammadat, G. Ungureanu, J. Öberg, and I. Sander, “Exploring Power and Throughput for Dataflow Applications on Predictable NoC Multiprocessors,” Aug. 2018, pp. 719–726. doi: 10.1109/DSD.2018.00011.
 *
 * Which are mostly (all?) present in the DeSyDe source code repository https://github.com/forsyde/DeSyDe,
 * in its examples folder.
 */
class SDFOnTileNoCUseCaseWithSolution extends AnyFunSuite {
  
  given ExecutionContext = ExecutionContext.global

  scribe.Logger.root
    .clearHandlers()
    .clearModifiers()
    .withHandler(minimumLevel = Some(Level.Debug), formatter = formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.italic(scribe.format.classNameSimple)} - ${scribe.format.message}")
    .replace()

  val explorationHandler = ExplorationHandler()
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler()
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  val forSyDeModelHandler = ForSyDeModelHandler().registerDriver(ForSyDeSDF3Driver())

  // the platform is done here in memory since the format used by the DeSyDe tool is non-standard,
  // even for the SDF3 group.
  val small2x2PlatformModel = {
    val m = ForSyDeSystemGraph()
    var niMesh = Array.fill(2)(Array.fill[InstrumentedCommunicationModule](2)(null))
    // put the microblaze elements
    for (i <- 0 until 3; row = i % 2; col = (i - row) / 2) {
        val tile = AbstractStructure.enforce(m.newVertex("tile_" + i))
        val tileVisu = GreyBox.enforce(tile)
        val proc = InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_" + i)))
        val mem = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_mem" + i)))
        val ni = InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_ni" + i)))
        proc.setOperatingFrequencyInHertz(50000000L)
        mem.setOperatingFrequencyInHertz(50000000L)
        mem.setSpaceInBits(1048576L * 8L)
        ni.setOperatingFrequencyInHertz(50000000L)
        ni.setFlitSizeInBits(128L)
        ni.setMaxConcurrentFlits(1)
        ni.setMaxCyclesPerFlit(4)
        ni.setInitialLatency(0L)
        proc.setModalInstructionsPerCycle(Map(
            "eco" -> Map(
                "all" -> (1.0 / 65.0).asInstanceOf[java.lang.Double]
            ).asJava,
            "default" -> Map(
                "all" -> (1.0 / 13.0).asInstanceOf[java.lang.Double]
            ).asJava
        ).asJava)
        // connect them
        tile.insertSubmodulesPort(m, proc)
        tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
        tile.insertSubmodulesPort(m, mem)
        tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
        tile.insertSubmodulesPort(m, ni)
        tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
        proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
        mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
        ni.getViewedVertex().addPorts("tileMemory", "tileProcessor")
        niMesh(row)(col) = ni
        m.connect(proc, ni, "networkInterface", "tileProcessor", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(ni, proc, "tileProcessor", "networkInterface", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(proc, mem, "defaultMemory", "instructionsAndData", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(mem, proc, "instructionsAndData", "defaultMemory", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(mem, ni, "networkInterface", "tileMemory", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(ni, mem, "tileMemory", "networkInterface", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    }
    // and now the Arm tile
    val armProc = InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("arm_cpu")))
    val armMem = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("arm_mem")))
    val armNi = InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("arm_ni")))
    armProc.setOperatingFrequencyInHertz(666667000L)
    armMem.setOperatingFrequencyInHertz(666667000L)
    armMem.setSpaceInBits(4294967296L * 8L)
    armNi.setOperatingFrequencyInHertz(666667000L)
    armNi.setFlitSizeInBits(128L)
    armNi.setMaxConcurrentFlits(1)
    armNi.setMaxCyclesPerFlit(4)
    armNi.setInitialLatency(0L)
    armProc.setModalInstructionsPerCycle(Map(
        "eco" -> Map(
            "all" -> (1.0 / 10.0).asInstanceOf[java.lang.Double]
        ).asJava,
        "default" -> Map(
            "all" -> (1.0).asInstanceOf[java.lang.Double]
        ).asJava
    ).asJava)
    // connect them
    armProc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
    armMem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
    armNi.getViewedVertex().addPorts("tileMemory", "tileProcessor")
    niMesh(1)(1) = armNi
    m.connect(armProc, armNi, "networkInterface", "tileProcessor", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    m.connect(armNi, armProc, "tileProcessor", "networkInterface", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    m.connect(armProc, armMem, "defaultMemory", "instructionsAndData", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    m.connect(armMem, armProc, "instructionsAndData", "defaultMemory", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    m.connect(armMem, armNi, "networkInterface", "tileMemory", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    m.connect(armNi, armMem, "tileMemory", "networkInterface", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)

    // and now we connect the NIs in the mesh
    for (
        i <- 0 until 4; row = i % 2; col = (i - row) / 2
    ) {
        if (row > 0) {
            val r = row - 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
            niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(r)(col), s"to_${r}_${col}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(r)(col), niMesh(row)(col), s"to_${row}_${col}", s"from_${r}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
        if (row < 1) {
            val r = row + 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
            niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(r)(col), s"to_${r}_${col}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(r)(col), niMesh(row)(col), s"to_${row}_${col}", s"from_${r}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
        if (col > 0) {
            val c = col - 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
            niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(row)(c), s"to_${row}_${c}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(row)(c), niMesh(row)(col), s"to_${row}_${col}", s"from_${c}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
        if (col < 1) {
            val c = col + 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
            niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(row)(c), s"to_${row}_${c}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(row)(c), niMesh(row)(col), s"to_${row}_${col}", s"from_${c}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
    }

    m
  }

  // and now we construct the bigger in memory for the same reason
  val large5x6PlatformModel = {
    val m = ForSyDeSystemGraph()
    var niMesh = Array.fill(5)(Array.fill[InstrumentedCommunicationModule](6)(null))
    // put the proc elements
    for (i <- 0 until 30; row = i % 5; col = (i - row) / 5) {
        val tile = AbstractStructure.enforce(m.newVertex("tile_" + i))
        val tileVisu = GreyBox.enforce(tile)
        val proc = InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("tile_cpu_" + i)))
        val mem = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("tile_mem" + i)))
        val ni = InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("tile_ni" + i)))
        proc.setOperatingFrequencyInHertz(50000000L)
        mem.setOperatingFrequencyInHertz(50000000L)
        mem.setSpaceInBits(16000 * 8L)
        ni.setOperatingFrequencyInHertz(50000000L)
        ni.setFlitSizeInBits(128L)
        ni.setMaxConcurrentFlits(1)
        ni.setMaxCyclesPerFlit(6)
        ni.setInitialLatency(0L)
        proc.setModalInstructionsPerCycle(Map(
            "eco" -> Map(
                "all" -> (1.0 / 1.2).asInstanceOf[java.lang.Double]
            ).asJava,
            "default" -> Map(
                "all" -> (1.0).asInstanceOf[java.lang.Double]
            ).asJava
        ).asJava)
        proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
        mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
        ni.getViewedVertex().addPorts("tileMemory", "tileProcessor")
        tile.insertSubmodulesPort(m, proc)
        tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
        tile.insertSubmodulesPort(m, mem)
        tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
        tile.insertSubmodulesPort(m, ni)
        tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
        // connect them
        niMesh(row)(col) = ni
        m.connect(proc, ni, "networkInterface", "tileProcessor", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(ni, proc, "tileProcessor", "networkInterface", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(proc, mem, "defaultMemory", "instructionsAndData", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(mem, proc, "instructionsAndData", "defaultMemory", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(mem, ni, "networkInterface", "tileMemory", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        m.connect(ni, mem, "tileMemory", "networkInterface", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
    }

    // and now we connect the NIs in the mesh
    for (
        i <- 0 until 30; row = i % 5; col = (i - row) / 5
    ) {
        if (row > 0) {
            val r = row - 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
            niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(r)(col), s"to_${r}_${col}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(r)(col), niMesh(row)(col), s"to_${row}_${col}", s"from_${r}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
        if (row < 4) {
            val r = row + 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
            niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(r)(col), s"to_${r}_${col}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(r)(col), niMesh(row)(col), s"to_${row}_${col}", s"from_${r}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
        if (col > 0) {
            val c = col - 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
            niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(row)(c), s"to_${row}_${c}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(row)(c), niMesh(row)(col), s"to_${row}_${col}", s"from_${c}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
        if (col < 5) {
            val c = col + 1;
            niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
            niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
            m.connect(niMesh(row)(col), niMesh(row)(c), s"to_${row}_${c}", s"from_${row}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
            m.connect(niMesh(row)(c), niMesh(row)(col), s"to_${row}_${col}", s"from_${c}_${col}", EdgeTrait.PLATFORM_PHYSICALCONNECTION, EdgeTrait.VISUALIZATION_VISUALCONNECTION)
        }
    }

    m
  }

  val sobelSDF3 = forSyDeModelHandler.loadModel("tests/models/sdf3/a_sobel.hsdf.xml")
  val susanSDF3 = forSyDeModelHandler.loadModel("tests/models/sdf3/b_susan.hsdf.xml")
  val rastaSDF3 = forSyDeModelHandler.loadModel("tests/models/sdf3/c_rasta.hsdf.xml")
  val jpegEnc1SDF3 = forSyDeModelHandler.loadModel("tests/models/sdf3/d_jpegEnc1.hsdf.xml")
  val g10_3_cyclicSDF3 = forSyDeModelHandler.loadModel("tests/models/sdf3/g10_3_cycl.sdf.xml")

  test("Created platform models in memory successfully and can write them out") {
    forSyDeModelHandler.writeModel(small2x2PlatformModel, "small_platform.fiodl")
    forSyDeModelHandler.writeModel(large5x6PlatformModel, "large_platform.fiodl")
  }

  test("Correct decision model identification of the Small platform") {
    lazy val identified = identificationHandler.identifyDecisionModels(small2x2PlatformModel)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[TiledDigitalHardware]).isDefined)
  }

  test("Correct decision model identification of the Large platform") {
    lazy val identified = identificationHandler.identifyDecisionModels(large5x6PlatformModel)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[TiledDigitalHardware]).isDefined)
  }

  test("Correct decision model identification of Sobel") {
    lazy val identified = identificationHandler.identifyDecisionModels(sobelSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val sobelDM = identified.find(m => m.isInstanceOf[SDFApplication]).map(m => m.asInstanceOf[SDFApplication]).get
    assert(sobelDM.repetitionVectors.head.sameElements(Array(1, 1, 1, 1)))
  }

  test("Correct decision model identification of SUSAN") {
    lazy val identified = identificationHandler.identifyDecisionModels(susanSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val susanDM = identified.find(m => m.isInstanceOf[SDFApplication]).map(m => m.asInstanceOf[SDFApplication]).get
    println(susanDM.repetitionVectors.head.mkString(", "))
    assert(susanDM.repetitionVectors.head.sameElements(Array(1, 1, 1, 1, 1)))
  }

  test("Correct decision model identification of RASTA") {
    lazy val identified = identificationHandler.identifyDecisionModels(rastaSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
  }

  test("Correct decision model identification of JPEG") {
    lazy val identified = identificationHandler.identifyDecisionModels(jpegEnc1SDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
  }

  test("Correct decision model identification of Synthetic") {
    lazy val identified = identificationHandler.identifyDecisionModels(g10_3_cyclicSDF3)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
  }

}
