import scala.sys.process._
import scala.collection.mutable.Buffer
import scala.jdk.CollectionConverters._

import $file.generate_platform

import $ivy.`org.jgrapht:jgrapht-core:1.5.1`
import $ivy.`io.github.forsyde:forsyde-io-java-core:0.6.0`
import $ivy.`io.github.forsyde:forsyde-io-java-sdf3:0.6.0`

import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.sdf3.drivers.ForSyDeSDF3Driver
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.AsWeightedGraph
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import forsyde.io.java.core.Vertex
import java.nio.file.Files
import java.util.stream.Collectors

val modelHandler = new ForSyDeModelHandler(new ForSyDeSDF3Driver())

val combinationsExp1 = Seq(
  Seq("c_rasta.hsdf.xml", "d_jpegEnc1.hsdf.xml"),
  Seq("a_sobel.hsdf.xml", "b_susan.hsdf.xml", "c_rasta.hsdf.xml"),
  Seq("a_sobel.hsdf.xml", "b_susan.hsdf.xml", "d_jpegEnc1.hsdf.xml"),
  Seq("a_sobel.hsdf.xml", "c_rasta.hsdf.xml", "d_jpegEnc1.hsdf.xml"),
  Seq("b_susan.hsdf.xml", "c_rasta.hsdf.xml", "d_jpegEnc1.hsdf.xml"),
  Seq("a_sobel.hsdf.xml", "b_susan.hsdf.xml", "c_rasta.hsdf.xml", "d_jpegEnc1.hsdf.xml"),
  Seq("a_sobel.hsdf.xml"),
  Seq("g10_3_cycl.sdf.xml")
)

val actorRange1 = 2 to 10
val coreRange1 = 2 to 8
val svrMultiplicationRange1 = Array(1.0)
val dataPointsPerTuple = 5
val maxNumExperiments1 = 100

val actorRange2 = (2 to 10) ++ (12 to 50 by 2)
val coreRange2 = (2 to 8)
val svrMultiplicationRange2 = Array(1.0, 2.0, 5.0, 10.0)

val actorRange3 = actorRange2 ++ (105 to 1000 by 5)
val coreRange3 = coreRange2 ++ (20 to 64 by 4)

val sdf3Gen =
  os.pwd / "sdf3" / "build" / "release" / "Linux" / "bin" / "sdf3generate-sdf"

/** This function was extracted from IDeSyDe's source code and adapted for the
  * experments generation.
  */
def computeWorstCaseDelays(model: ForSyDeSystemGraph): Array[Array[Long]] = {
  val gBuilder =
    SimpleDirectedWeightedGraph.createBuilder[Vertex, DefaultEdge](() =>
      new DefaultEdge()
    )
  model
    .edgeSet()
    .forEach(e => {
      val src = model.getEdgeSource(e)
      val dst = model.getEdgeTarget(e)
      GenericCommunicationModule
        .safeCast(src)
        .ifPresent(srcRouter => {
          InstrumentedCommunicationModule
            .safeCast(dst)
            .ifPresent(dstRouter => {
              val minBw = dstRouter
                .getMaxCyclesPerFlit()
                .toDouble / dstRouter.getOperatingFrequencyInHertz().toDouble
              gBuilder.addEdge(
                src,
                dst,
                minBw
              )
            })
          GenericProcessingModule
            .safeCast(dst)
            .ifPresent(dstCore => {
              gBuilder.addEdge(
                src,
                dst,
                0.0
              )
            })
        })
      GenericProcessingModule
        .safeCast(src)
        .ifPresent(srcCore => {
          InstrumentedCommunicationModule
            .safeCast(dst)
            .ifPresent(dstRouter => {
              val minBw = dstRouter
                .getMaxCyclesPerFlit()
                .toDouble / dstRouter.getOperatingFrequencyInHertz().toDouble
              gBuilder.addEdge(
                src,
                dst,
                minBw.toDouble
              )
            })
          GenericProcessingModule
            .safeCast(dst)
            .ifPresent(dstCore => {
              gBuilder.addEdge(
                src,
                dst,
                0.0
              )
            })
        })
    })
  val directedAndConnectedMaxTimeGraph = gBuilder.buildAsUnmodifiable()
  val maxWeight = directedAndConnectedMaxTimeGraph.edgeSet.stream
    .mapToDouble(e => directedAndConnectedMaxTimeGraph.getEdgeWeight(e))
    .max
    .orElse(0.0)
  val reversedGraph = new AsWeightedGraph[Vertex, DefaultEdge](
    directedAndConnectedMaxTimeGraph,
    (e) => maxWeight - directedAndConnectedMaxTimeGraph.getEdgeWeight(e),
    true,
    false
  )
  val paths = new FloydWarshallShortestPaths(reversedGraph)
  var cores = Buffer[Vertex]()
  directedAndConnectedMaxTimeGraph
    .vertexSet()
    .forEach(vertex => {
      if (GenericProcessingModule.conforms(vertex)) cores += vertex
    })
  cores.toArray.map(src => {
    cores.toArray.map(dst => {
      if (src != dst && paths.getPath(src, dst) != null) {
        ((maxWeight * paths.getPath(src, dst).getLength()) - paths
          .getPathWeight(src, dst)).ceil.toLong
      } else
        -1
    })
  })
}

def getDeSyDePlatformInput(model: ForSyDeSystemGraph): String = {
  var cores = Buffer[(GenericProcessingModule, Long)]()
  var xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
  xmlString += "<platform name=\"demo_platform\">\n"
  model
    .vertexSet()
    .forEach(vv => {
      GenericProcessingModule
        .safeCast(vv)
        .ifPresent(cpu => {
          var memory = 0L
          model
            .outgoingEdgesOf(vv)
            .stream()
            .map(e => model.getEdgeTarget(e))
            .flatMap(out => GenericMemoryModule.safeCast(out).stream())
            .forEach(mem => {
              memory += mem.getSpaceInBits()
            })
          cores +:= (cpu, memory)
        })
    })
  cores.foreach(tuple => {
    val cpu = tuple._1
    val maxSize = tuple._2
    xmlString += s"  <processor model=\"${cpu.getIdentifier()}\" number=\"1\">\n"
    xmlString += s"    <mode name=\"default\" cycle=\"${1}\" mem=\"${maxSize}\" dynPower=\"0\" staticPower=\"1\" area=\"0\" monetary=\"0\"/>\n"
    xmlString += s"  </processor>\n"
  })
  xmlString += s"""<interconnect>
                    |    <TDMA_bus name="TDMA-bus" x-dimension="${cores.size}" flitSize="32" tdma_slots="${cores.size}" maxSlotsPerProc="${cores.size}">
                    |        <mode name="default" cycleLength="1"/>
                    |    </TDMA_bus>
                    |</interconnect>""".stripMargin
  xmlString + "</platform>"
}

def getSDF3PlatformInput(model: ForSyDeSystemGraph): String = {
  var tiles = Buffer[
    (GenericProcessingModule, GenericMemoryModule, GenericCommunicationModule)
  ]()
  var xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
  xmlString += "<sdf3 xmlns:xsi=\"https://www.w3.org/2001/XMLSchema-instance\" version=\"1.0\" type=\"sdf\" xsi:noNamespaceSchemaLocation=\"https://www.es.ele.tue.nl/sdf3/xsd/sdf3-sdf.xsd\">\n"
  xmlString += "  <architectureGraph name=\"arch\">\n"
  model
    .vertexSet()
    .forEach(vv => {
      GenericProcessingModule
        .safeCast(vv)
        .ifPresent(cpu => {
          val memory = model
            .outgoingEdgesOf(vv)
            .stream()
            .map(e => model.getEdgeTarget(e))
            .flatMap(out => GenericMemoryModule.safeCast(out).stream())
            .findAny()
            .get()
          val ni = model
            .outgoingEdgesOf(vv)
            .stream()
            .map(e => model.getEdgeTarget(e))
            .flatMap(out => GenericCommunicationModule.safeCast(out).stream())
            .findAny()
            .get()
          tiles +:= (cpu, memory, ni)
        })
    })
  tiles.zipWithIndex.foreach(tileTuple => {
    val (tuple, tileNum) = tileTuple
    val (cpu, mem, ni) = tuple
    val maxSize = mem.getSpaceInBits()
    val niIns = InstrumentedCommunicationModule.safeCast(ni).get()
    val bw =
      (niIns.getFlitSizeInBits() * niIns.getMaxConcurrentFlits().toLong * niIns
        .getOperatingFrequencyInHertz()) / niIns.getMaxCyclesPerFlit().toLong
    xmlString += s"    <tile name=\"t${tileNum}\">\n"
    xmlString += s"      <processor name=\"${cpu.getIdentifier()}\" type=\"proc_0\">\n"
    xmlString += "        <arbitration type=\"TDMA\" wheelsize=\"100000\"/>\n"
    xmlString += s"      </processor>\n"
    xmlString += s"      <memory name=\"${mem.getIdentifier()}\" size=\"${maxSize}\"/>\n"
    xmlString += s"      <networkInterface name=\"${ni.getIdentifier()}\" nrConnections=\"${niIns.getMaxConcurrentFlits()}\" inBandwidth=\"${bw}\" outBandwidth=\"${bw}\"/>\n"
    xmlString += "    </tile>\n"
  })
  val wccts = computeWorstCaseDelays(model)
  tiles.zipWithIndex.foreach(srcTuple => {
    val (srcTile, src) = srcTuple
    tiles.zipWithIndex.foreach(dstTuple => {
      val (dstTile, dst) = dstTuple
      if (wccts(src)(dst) > -1) {
        xmlString += s"    <connection name=\"t${src}-t${dst}\" srcTile=\"t$src\" dstTile=\"t$dst\" delay=\"${wccts(src)(dst)}\"/>\n"
      }
    })
  })
  val minFlitSize = model
    .vertexSet()
    .stream()
    .flatMap(v => InstrumentedCommunicationModule.safeCast(v).stream())
    .mapToLong(i => i.getFlitSizeInBits())
    .max()
    .orElse(32L)
  xmlString += s"  <network slotTableSize=\"${tiles.length}\" packetHeaderSize=\"${32}\" flitSize=\"$minFlitSize\" reconfigurationTimeNI=\"${tiles.length}\">\n"
  // tiles.zipWithIndex.foreach(tuple => {
  //     val (t, ti) = tuple
  //     model.outgoingEdgesOf(t._1.getViewedVertex()).forEach(e => {
  //         val dst = model.getEdgeTarget(e)
  //         GenericCommunicationModule.safeCast(dst).ifPresent(dstRouter => {
  //             xmlString += s"    <link name=\"t${ti}-${dstRouter.getIdentifier()}\" src=\"t${ti}\" dst=\"${dstRouter.getIdentifier()}\"/>\n"
  //         })
  //     })
  //     model.incomingEdgesOf(t._1.getViewedVertex()).forEach(e => {
  //         val src = model.getEdgeSource(e)
  //         GenericCommunicationModule.safeCast(src).ifPresent(srcRouter => {
  //             xmlString += s"    <link name=\"${srcRouter.getIdentifier()}-t${ti}\" dst=\"t${ti}\" src=\"${srcRouter.getIdentifier()}\"/>\n"
  //         })
  //     })
  // })
  model
    .vertexSet()
    .forEach(v => {
      GenericCommunicationModule
        .safeCast(v)
        .ifPresent(ce => {
          if (!tiles.exists(tuple => tuple._3 == ce)) {
            xmlString += s"    <router name=\"${ce.getIdentifier()}\"/>\n"
          }
        })
    })
  model
    .edgeSet()
    .forEach(e => {
      val src = model.getEdgeSource(e)
      val dst = model.getEdgeTarget(e)
      GenericCommunicationModule
        .safeCast(src)
        .ifPresent(srcRouter => {
          GenericCommunicationModule
            .safeCast(dst)
            .ifPresent(dstRouter => {
              if (tiles.exists(tuple => tuple._3 == srcRouter)) {
                val tileIdx = tiles.indexWhere(tuple => tuple._3 == srcRouter)
                xmlString += s"    <link name=\"t${tileIdx}-${dst.getIdentifier()}\" src=\"t${tileIdx}\" dst=\"${dst.getIdentifier()}\"/>\n"
              } else if (tiles.exists(tuple => tuple._3 == dstRouter)) {
                val tileIdx = tiles.indexWhere(tuple => tuple._3 == dstRouter)
                xmlString += s"    <link name=\"${src.getIdentifier()}-t${tileIdx}\" src=\"${src.getIdentifier()}\" dst=\"t${tileIdx}\"/>\n"
              } else {
                xmlString += s"    <link name=\"${src.getIdentifier()}-${dst.getIdentifier()}\" src=\"${src.getIdentifier()}\" dst=\"${dst.getIdentifier()}\"/>\n"
              }
            })
        })
    })
  xmlString += "  </network>\n"
  xmlString += "  </architectureGraph>\n"
  xmlString + "</sdf3>"
}

def getSDF3FlowInput(model: ForSyDeSystemGraph): String = {
  var tiles = Buffer[GenericProcessingModule]()
  model
    .vertexSet()
    .forEach(vv => {
      GenericProcessingModule.safeCast(vv).ifPresent(cpu => { tiles +:= cpu })
    })
  var xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
  xmlString += """<sdf3 xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance" version="1.0" type="sdf" xsi:noNamespaceSchemaLocation="https://www.es.ele.tue.nl/sdf3/xsd/sdf3-sdf.xsd">\n"""
  xmlString += s"""  <settings type="flow">
                    |    <flowType type="NSoC"/>
                    |    <applicationGraph file="sdfs/applications_input.sdf3.xml"/>
                    |    <architectureGraph file="sdf3_platform_input.sdf3.xml"/>
                    |    <tileMapping algo="loadbalance">
                    |        <constants>
                    |        <constant name="a" value="1.0"/> <!-- processing -->
                    |        <constant name="b" value="0.0"/> <!-- memory -->
                    |        <constant name="f" value="0.0"/> <!-- communication -->
                    |        <constant name="g" value="0.0"/> <!-- latency -->
                    |        </constants>
                    |    </tileMapping>
                    |    <nocMapping algo="greedy">
                    |        <constraints>
                    |            <maxDetour d="${tiles.length}"/>
                    |            <maxNrRipups n="${tiles.length}"/>
                    |            <maxNrTries n="0"/>
                    |        </constraints>
                    |    </nocMapping>
                    |  </settings>\n""".stripMargin
  xmlString + "</sdf3>"
}
def getSdfGenerationInput(nActors: Int, vecSum: Int): String = {
  s"""<?xml version="1.0" encoding="UTF-8"?>
       |<sdf3 xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance" version="1.0" type="sdf" xsi:noNamespaceSchemaLocation="https://www.es.ele.tue.nl/sdf3/xsd/sdf3-sdf.xsd">
       |    <settings type='generate'>
       |        <graph>
       |            <actors nr='$nActors'/>
       |            <degree avg='${nActors / 2}' var='${nActors / 2 - 1}' min='1' max='$nActors'/>
       |            <rate avg='${nActors / 2}' var='${nActors / 2 - 1}' min='1' max='$nActors' repetitionVectorSum='$vecSum'/>
       |            <initialTokens prop='0'/>
       |            <structure stronglyConnected='false' acyclic='true' multigraph='true'/>
       |        </graph>
       |        <graphProperties>
       |            <procs nrTypes='1' mapChance='0.5'/>
       |            <execTime avg='${nActors * 10}' var='$nActors' min='1' max='${nActors * 10}'/>
       |            <stateSize avg='2048' var='1024' min='512' max='8192'/>
       |            <tokenSize avg='128' var='32' min='8' max='512'/>
       |        </graphProperties>
       |    </settings>
       |</sdf3>""".stripMargin
}

def generateDSEConfig(expPath: os.Path): String =
  s"""# input file or path. Multiple paths allowed.
       |inputs=${expPath.toString()}/sdfs/
       |inputs=${expPath.toString()}/xmls/
       |output=${expPath.toString()}/desyde_output/
       |log-file=${expPath.toString()}/desyde_output.log
       |output-file-type=ALL_OUT
       |output-print-frequency=ALL_SOL
       |log-level=INFO
       |log-level=DEBUG
       |[presolver]
       |model=ONE_PROC_MAPPINGS
       |search=OPTIMIZE
       |[dse]
       |model=SDF_PR_ONLINE
       |search=OPTIMIZE
       |criteria=THROUGHPUT
       |# search timeout. 0 means infinite. If two values are provided, the
       |# first one specifies the timeout for the first solution, and the
       |# second one for all solutions.
       |timeout=432000
       |timeout=432000
       |threads=1
       |noGoodDepth=75
       |luby_scale=100
       |th_prop=MCR""".stripMargin

/** This function was extracted from IDeSyDe's source code and adapted for the
  * experments generation.
  */
def computeWCETTable(
    model: ForSyDeSystemGraph,
    timeMultiplier: Long = 1000000000L
): String = {
  var string = "<WCET_table>\n"
  model
    .vertexSet()
    .forEach(v => {
      SDFActor
        .safeCast(v)
        .flatMap(actor => InstrumentedExecutable.safeCast(actor))
        .ifPresent(iactor => {
          string += s"  <mapping task_type=\"${iactor.getIdentifier()}\">\n"
          model
            .vertexSet()
            .forEach(vv => {
              GenericProcessingModule
                .safeCast(vv)
                .flatMap(cpu => InstrumentedProcessingModule.safeCast(cpu))
                .ifPresent(icpu => {
                  var minWcet = Double.MaxValue
                  iactor
                    .getOperationRequirements()
                    .forEach((opGroup, opNeeds) => {
                      icpu
                        .getModalInstructionsPerCycle()
                        .forEach((ipcGroup, ipc) => {
                          if (
                            opNeeds
                              .keySet()
                              .asScala
                              .subsetOf(ipc.keySet().asScala)
                          ) {
                            var wcet = 0.0
                            opNeeds.forEach((k, v) => wcet += v / ipc.get(k))
                            wcet = wcet / icpu.getOperatingFrequencyInHertz()
                            minWcet = Math.min(wcet, minWcet)
                          }
                        })
                    })
                  if (minWcet < Int.MaxValue) {
                    string += s"    <wcet processor=\"${icpu.getIdentifier()}\" mode=\"default\" wcet=\"${(timeMultiplier * minWcet).toLong}\"/>\n"
                  }
                })
            })
          string += "  </mapping>\n"
        })
    })
  string + "</WCET_table>"
}

@main
def generate_idesyde_1(): Unit = {
  val rootFolder = os.pwd / "sdfComparison" 
  for (cores <- coreRange1) {
    val idesydePlatform =
      generate_platform.makeTDMASingleBusPlatform(cores, 32L)
    for (actors <- actorRange1; q <- svrMultiplicationRange1) {
      val sdf3SDFGen = getSdfGenerationInput(actors, (actors * q).ceil.toInt)
      val appFolder = rootFolder / s"actors_${actors}" / s"svr_${(q*100).toInt}" 
      val sdfGenFile = (appFolder / "sdf3_gen.xml").toNIO
      os.makeDir.all(appFolder)
      java.nio.file.Files.writeString(
        sdfGenFile,
        sdf3SDFGen,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
      )
      for ( exp <- 1 to dataPointsPerTuple) {
        val combinationFolder =
        appFolder / s"plat_${cores}" / s"exp_${exp}"
        val idesydeFullInput = (combinationFolder / "idesyde_input.fiodl").toNIO
        val sdfAppFile = (combinationFolder / "sdfs" / "applications_input.sdf3.xml").toNIO
        os.makeDir.all(combinationFolder / "sdfs")
        if (!java.nio.file.Files.exists(sdfAppFile)) {
          Seq(
            sdf3Gen.toString(),
            "--settings",
            sdfGenFile.toString(),
            "--output",
            sdfAppFile.toString()
          ).!
        }
        if (!java.nio.file.Files.exists(idesydeFullInput)) {
          println("loading " + sdfAppFile)
          val sdfApp = modelHandler.loadModel(sdfAppFile)
          val dseProblem = sdfApp.merge(idesydePlatform)
          modelHandler.writeModel(dseProblem, idesydeFullInput)
        }
      }
    }
  }
}

@main
def generate_desyde_1(): Unit = {
  val rootFolder = os.pwd / "sdfComparison" 
  for (cores <- coreRange1) {
    val idesydePlatform =
      generate_platform.makeTDMASingleBusPlatform(cores, 32L)
    val desydePlatform = getDeSyDePlatformInput(idesydePlatform)
    for (actors <- actorRange1; q <- svrMultiplicationRange1) {
      val sdf3SDFGen = getSdfGenerationInput(actors, (actors * q).ceil.toInt)
      val appFolder = rootFolder / s"actors_${actors}" / s"svr_${(q*100).toInt}" 
      val sdfGenFile = (appFolder / "sdf3_gen.xml").toNIO
      os.makeDir.all(appFolder)
      java.nio.file.Files.writeString(
        sdfGenFile,
        sdf3SDFGen,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
      )
      for ( exp <- 1 to dataPointsPerTuple) {
        val combinationFolder = appFolder / s"plat_${cores}" / s"exp_${exp}"
        os.makeDir.all(combinationFolder / "sdfs")
        os.makeDir.all(combinationFolder / "xmls")
        val sdfAppFile = (combinationFolder / "sdfs" / "applications_input.sdf3.xml").toNIO
        val desydeFile =
          (combinationFolder / "xmls" / "desyde_platform_input.xml").toNIO
        val wcetTableFile = (combinationFolder / "xmls" / "WCETs.xml").toNIO
        val configFile = (combinationFolder / "config.cfg").toNIO
        if (!java.nio.file.Files.exists(sdfAppFile)) {
          Seq(
            sdf3Gen.toString(),
            "--settings",
            sdfGenFile.toString(),
            "--output",
            sdfAppFile.toString()
          ).!
        }
        val sdfApp = modelHandler.loadModel(sdfAppFile)
        val dseProblem = sdfApp.merge(idesydePlatform)
        val wcetTable = computeWCETTable(dseProblem)
        val configString = generateDSEConfig(combinationFolder)
        java.nio.file.Files.writeString(
          desydeFile,
          desydePlatform,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        )
        java.nio.file.Files.writeString(
          configFile,
          configString,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        )
        java.nio.file.Files.writeString(
          wcetTableFile,
          wcetTable,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        )
      }
    }
  }
}

@main
def generate_idesyde_2(): Unit = {
  val rootFolder = os.pwd / "sdfScalability" 
  for (cores <- coreRange2) {
    val idesydePlatform =
      generate_platform.makeTDMASingleBusPlatform(cores, 32L)
    for (actors <- actorRange2; q <- svrMultiplicationRange2) {
      val sdf3SDFGen = getSdfGenerationInput(actors, (actors * q).ceil.toInt)
      val appFolder = rootFolder / s"actors_${actors}" / s"svr_${(q*100).toInt}" 
      val sdfGenFile = (appFolder / "sdf3_gen.xml").toNIO
      os.makeDir.all(appFolder)
      java.nio.file.Files.writeString(
        sdfGenFile,
        sdf3SDFGen,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
      )
      for ( exp <- 1 to dataPointsPerTuple) {
        val combinationFolder =
        appFolder / s"plat_${cores}" / s"exp_${exp}"
        val idesydeFullInput = (combinationFolder / "idesyde_input.fiodl").toNIO
        val sdfAppFile = (combinationFolder / "sdfs" / "applications_input.sdf3.xml").toNIO
        os.makeDir.all(combinationFolder / "sdfs")
        if (!java.nio.file.Files.exists(sdfAppFile) || java.nio.file.Files.lines(sdfAppFile).count() == 0) {
          Seq(
            sdf3Gen.toString(),
            "--settings",
            sdfGenFile.toString(),
            "--output",
            sdfAppFile.toString()
          ).!
        }
        if (!java.nio.file.Files.exists(idesydeFullInput)) {
          val sdfApp = modelHandler.loadModel(sdfAppFile)
          val dseProblem = sdfApp.merge(idesydePlatform)
          modelHandler.writeModel(dseProblem, idesydeFullInput)
        }
      }
    }
  }
}

@main
def generate_idesyde_3(cores: Int = 8): Unit = {
  val rootFolder = os.pwd // / "caseStudies" 
  val sdfsFolder = os.pwd / "sdfs"
  for (comb <- combinationsExp1) {
    val combinationFolder = rootFolder / comb.map(_.split("\\.").head).reduce(_ + "_" + _)
    val idesydeFullInput = (combinationFolder / "idesyde_input.fiodl").toNIO
    val idesydePlatform =
      generate_platform.makeTDMASingleBusPlatform(cores, 32L, 1L, 1.0, 100L)
    // make the last core an accelerator which supports only CS
    idesydePlatform.queryVertex(s"micro_blaze_${cores - 1}").flatMap(InstrumentedProcessingModule.safeCast(_)).ifPresent(ipe => {
      ipe.setModalInstructionsPerCycle(Map("default" -> Map("CS" -> (1.0).asInstanceOf[java.lang.Double]).asJava).asJava)
    })
    val apps = comb.map(f => modelHandler.loadModel((sdfsFolder / f).toNIO)).reduce(_.merge(_))
    // add the HW acc support for CS of JPEG
    apps.queryVertex("CS_0").flatMap(InstrumentedExecutable.safeCast(_)).ifPresent(iproc => {
      val cur = iproc.getOperationRequirements()
      cur.put("hwacc", Map("CS" -> 1388L.asInstanceOf[java.lang.Long]).asJava)
      iproc.setOperationRequirements(cur)
    })
    val dseProblem = idesydePlatform.merge(apps)
    os.makeDir.all(combinationFolder)
    modelHandler.writeModel(dseProblem, idesydeFullInput)
  }
}

@main
def generate(): Unit = {
    // generate_desyde_1()
    // generate_idesyde_1()
    // generate_idesyde_2()
    generate_idesyde_3()
}
