package idesyde.identification.models.platform

import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*

import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.Graph
import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.platform.TiledMultiCorePlatformMixin
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.core.Vertex
import org.jgrapht.graph.SimpleDirectedGraph
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.platform.RoundRobinCommunicationModule
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import idesyde.identification.models.platform.InstrumentedPlatformMixin
import scala.collection.mutable

final case class TiledDigitalHardware(
    val processors: Array[GenericProcessingModule],
    val memories: Array[GenericMemoryModule],
    val networkInterfaces: Array[GenericCommunicationModule],
    val routers: Array[GenericCommunicationModule],
    val interconnectTopology: Graph[GenericCommunicationModule, DefaultEdge]
)(using Fractional[BigFraction])(using conversion: Conversion[Double, BigFraction])
    extends ForSyDeDecisionModel
    with TiledMultiCorePlatformMixin[Long, BigFraction]
    with InstrumentedPlatformMixin {

  def coveredVertexes: Iterable[Vertex] = processors.map(_.getViewedVertex()) ++ memories.map(
    _.getViewedVertex()
  ) ++ networkInterfaces.map(_.getViewedVertex()) ++ routers.map(_.getViewedVertex())

  def allCommElems: Array[GenericCommunicationModule] = networkInterfaces ++ routers

  def architectureGraph: Graph[Int, DefaultEdge] = {
    val gBuilder = SimpleDirectedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    // add the integer encoded connections between cores and NIs
    tileSet.foreach(tileIdx => gBuilder.addEdge(tileIdx, tileSet.size + tileIdx))
    // now connect the actual NIs
    val allCE = allCommElems
    interconnectTopology
      .edgeSet()
      .forEach(e => {
        val srcNI = tileSet.size + allCE.indexOf(interconnectTopology.getEdgeSource(e))
        val dstNI = tileSet.size + allCE.indexOf(interconnectTopology.getEdgeTarget(e))
        gBuilder.addEdge(srcNI, dstNI)
      })
    gBuilder.buildAsUnmodifiable()
  }
  def architectureGraphMaximumHopTime(src: Int, dst: Int): BigFraction = BigFraction.ZERO

  def architectureGraphMinimumHopTime(src: Int, dst: Int): BigFraction = BigFraction.ZERO
  def maxMemoryPerTile: Array[Long] = memories.map(mem => mem.getSpaceInBits())
  def maxTimePerInstructionPerTilePerMode: Array[Map[String, Map[String, BigFraction]]] =
    processors.map(pe => {
      InstrumentedProcessingModule
        .safeCast(pe)
        .map(peInst => {
          peInst.getModalInstructionsPerCycle.asScala
            .map((mode, ipc) =>
              mode -> ipc.asScala
                .map((instruction, cycles) => instruction -> BigFraction(cycles))
                .toMap
            )
            .toMap
        })
        .orElse(Map.empty[String, Map[String, BigFraction]])
    })

  def maxTraversalTimePerBitPerRouter: Array[BigFraction] = networkInterfaces.map(ni => {
    InstrumentedCommunicationModule
      .safeCast(ni)
      .map(insce =>
        BigFraction.ONE
          .multiply(insce.getFlitSizeInBits.toInt)
          // .multiply(insce.getMaxConcurrentFlits.toInt)
          .multiply(insce.getOperatingFrequencyInHertz.toInt)
          .divide(insce.getMaxCyclesPerFlit.toInt)
          .reciprocal()
      )
      .orElse(BigFraction.ZERO)
  })

  def minTraversalTimePerBitPerRouter: Array[BigFraction] = networkInterfaces.map(ni => {
    InstrumentedCommunicationModule
      .safeCast(ni)
      .map(insce =>
        BigFraction.ONE
          .multiply(insce.getFlitSizeInBits.toInt)
          .multiply(insce.getMaxConcurrentFlits.toInt)
          .multiply(insce.getOperatingFrequencyInHertz.toInt)
          .divide(insce.getMaxCyclesPerFlit.toInt)
          .reciprocal()
      )
      .orElse(BigFraction.ZERO)
  })
  def tileSet: Array[Int] = (0 until processors.size).toArray
  def routerSet: Array[Int] =
    (processors.size until (processors.size + networkInterfaces.size + routers.size)).toArray

  def processorsFrequency: Array[Long] = processors.map(_.getOperatingFrequencyInHertz())

  def processorsProvisions: Array[Map[String, Map[String, Double]]] = processors.map(pe => {
    // we do it mutable for simplicity...
    // the performance hit should not be a concern now, for super big instances, this can be reviewed
    var mutMap = mutable.Map[String, Map[String, Double]]()
    InstrumentedProcessingModule.safeCast(pe).map(ipe => {
        ipe.getModalInstructionsPerCycle().entrySet().forEach(e => {
            mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Double]).toMap
        })
    })
    mutMap.toMap
  })

  def uniqueIdentifier: String = "TiledDigitalHardware"
}
