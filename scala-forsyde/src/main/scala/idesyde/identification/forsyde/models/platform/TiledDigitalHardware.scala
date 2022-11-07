package idesyde.identification.forsyde.models.platform

import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*

import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.Graph
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.Vertex
import org.jgrapht.graph.SimpleDirectedGraph
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.platform.RoundRobinCommunicationModule
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import scala.collection.mutable
import spire.math.Rational
import spire.implicits.*
import idesyde.identification.common.models.platform.InstrumentedPlatformMixin
import idesyde.identification.common.models.platform.TiledMultiCorePlatformMixin
import scala.collection.mutable.Queue

final case class TiledDigitalHardware(
    val processors: Array[GenericProcessingModule],
    val memories: Array[GenericMemoryModule],
    val networkInterfaces: Array[GenericCommunicationModule],
    val routers: Array[GenericCommunicationModule],
    val interconnectTopology: Graph[GenericCommunicationModule, DefaultEdge]
)(using Fractional[Rational])(using conversion: Conversion[Double, Rational])
    extends ForSyDeDecisionModel
    with TiledMultiCorePlatformMixin[Long, Rational]
    with InstrumentedPlatformMixin[Rational] {

  val coveredElements = (processors.map(_.getViewedVertex()) ++ memories.map(
    _.getViewedVertex()
  ) ++ networkInterfaces.map(_.getViewedVertex()) ++ routers.map(_.getViewedVertex())).toSet

  val coveredElementRelations = Set()

  val allCommElems: Array[GenericCommunicationModule] = networkInterfaces ++ routers
  val architectureGraph: Graph[Int, DefaultEdge] = {
    val gBuilder = SimpleDirectedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    // add the integer encoded connections between cores and NIs
    processors.zipWithIndex.foreach((p, tileIdx) => {
      gBuilder.addEdge(tileIdx, processors.size + tileIdx)
      gBuilder.addEdge(processors.size + tileIdx, tileIdx)
    })
    // now connect the actual NIs
    val allCE = allCommElems
    interconnectTopology
      .edgeSet()
      .forEach(e => {
        val srcNI = processors.size + allCE.indexOf(interconnectTopology.getEdgeSource(e))
        val dstNI = processors.size + allCE.indexOf(interconnectTopology.getEdgeTarget(e))
        gBuilder.addEdge(srcNI, dstNI)
      })
    gBuilder.buildAsUnmodifiable()
  }

  val tileSet: Array[Int] = (0 until processors.size).toArray

  val routerSet: Array[Int] =
    (processors.size until processors.size + networkInterfaces.size + routers.size).toArray

  def architectureGraphMaximumHopTime(src: Int, dst: Int): Rational = Rational.zero

  def architectureGraphMinimumHopTime(src: Int, dst: Int): Rational = Rational.zero
  val maxMemoryPerTile: Array[Long] = memories.map(mem => mem.getSpaceInBits())
  val maxTimePerInstructionPerTilePerMode: Array[Map[String, Map[String, Rational]]] =
    processors.map(pe => {
      InstrumentedProcessingModule
        .safeCast(pe)
        .map(peInst => {
          peInst.getModalInstructionsPerCycle.asScala
            .map((mode, ipc) =>
              mode -> ipc.asScala
                .map((instruction, cycles) => instruction -> Rational(cycles))
                .toMap
            )
            .toMap
        })
        .orElse(Map.empty[String, Map[String, Rational]])
    })

  val maxTraversalTimePerBitPerRouter: Array[Rational] = (networkInterfaces ++ routers).map(ni => {
    InstrumentedCommunicationModule
      .safeCast(ni)
      .map(insce =>
        (Rational.one
          * insce.getFlitSizeInBits.toInt
          // .multiply(insce.getMaxConcurrentFlits.toInt)
          * insce.getOperatingFrequencyInHertz.toInt
          / insce.getMaxCyclesPerFlit.toInt).reciprocal
      )
      .orElse(Rational.zero)
  })

  val minTraversalTimePerBitPerRouter: Array[Rational] = (networkInterfaces ++ routers).map(ni => {
    InstrumentedCommunicationModule
      .safeCast(ni)
      .map(insce =>
        (Rational.one
          * insce.getFlitSizeInBits.toInt
          * insce.getMaxConcurrentFlits.toInt
          * insce.getOperatingFrequencyInHertz.toInt
          / insce.getMaxCyclesPerFlit.toInt).reciprocal
      )
      .orElse(Rational.zero)
  })

  val processorsFrequency: Array[Long] = processors.map(_.getOperatingFrequencyInHertz())

  val processorsProvisions: Array[Map[String, Map[String, Rational]]] = processors.map(pe => {
    // we do it mutable for simplicity...
    // the performance hit should not be a concern now, for super big instances, this can be reviewed
    var mutMap = mutable.Map[String, Map[String, Rational]]()
    InstrumentedProcessingModule
      .safeCast(pe)
      .map(ipe => {
        ipe
          .getModalInstructionsPerCycle()
          .entrySet()
          .forEach(e => {
            mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> Rational(v)).toMap
          })
      })
    mutMap.toMap
  })

  val commElemsVirtualChannels: Array[Int] = allCommElems.map(r => {
    InstrumentedCommunicationModule
      .safeCast(r)
      .map(ir => ir.getMaxConcurrentFlits().toInt)
      .orElse(1)
  })
  def commElemsVirtualChannelsById(ceId: Int) = commElemsVirtualChannels(routerSet.indexOf(ceId))

  val bandWidthPerCEPerVirtualChannel: Array[Rational] = allCommElems.map(r => {
    InstrumentedCommunicationModule
      .safeCast(r)
      .map(ir =>
        Rational(
          ir.getFlitSizeInBits.toInt * ir.getOperatingFrequencyInHertz.toLong,
          ir.getMaxCyclesPerFlit.toInt
        ).reciprocal
      )
      .orElse(Rational.one)
  })

  def bandWidthPerCEPerVirtualChannelById(ceId: Int) = bandWidthPerCEPerVirtualChannel(
    routerSet.indexOf(ceId)
  )

  // save router paths so that it does not recompute them everytime
  val routerPaths = computeRouterPaths

  def symmetricTileGroups: Set[Set[Int]] = {
    val wccts = maxTraversalTimePerBit
    val outgoingWCCThistograms =
      wccts.map(dsts => dsts.groupBy(t => t).map((k, v) => k -> v.length))
    val incomingWCCThistograms =
      tileSet.map(dst =>
        tileSet.map(src => wccts(src)(dst)).groupBy(t => t).map((k, v) => k -> v.length)
      )
    var groups      = mutable.Set[Set[Int]]()
    var toBeMatched = tileSet.toSet
    while (!toBeMatched.isEmpty) {
      val t = toBeMatched.head
      val otherSymmetric = toBeMatched.tail
        .filter(tt => {
          val tIdx  = tileSet.indexOf(t)
          val ttIdx = tileSet.indexOf(tt)
          processorsProvisions(tIdx) == processorsProvisions(ttIdx) &&
          outgoingWCCThistograms(tIdx) == outgoingWCCThistograms(ttIdx) &&
          incomingWCCThistograms(tIdx) == incomingWCCThistograms(ttIdx)
        })
      toBeMatched -= t
      toBeMatched --= otherSymmetric
      groups += (otherSymmetric + t)
    }
    groups.toSet
  }

  def uniqueIdentifier: String = "TiledDigitalHardware"
}
