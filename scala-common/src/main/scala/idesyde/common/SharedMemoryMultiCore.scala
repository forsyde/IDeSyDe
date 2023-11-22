package idesyde.common

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import spire.math.Rational
import spire.implicits.*
import idesyde.core.DecisionModel
import idesyde.common.InstrumentedPlatformMixin
import idesyde.core.DecisionModel
import upickle.default._
import upickle.implicits.key
import java.{util => ju}
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.AsSubgraph

final case class SharedMemoryMultiCore(
    @key("processing_elems") val processingElems: Vector[String],
    @key("storage_elems") val storageElems: Vector[String],
    @key("communication_elems") val communicationElems: Vector[String],
    @key("topology_srcs") val topologySrcs: Vector[String],
    @key("topology_dsts") val topologyDsts: Vector[String],
    @key("processors_frequency") val processorsFrequency: Vector[Long],
    @key("processors_provisions") val processorsProvisions: Vector[
      Map[String, Map[String, Double]]
    ],
    @key("storage_sizes") val storageSizes: Vector[Long],
    @key("communication_elements_max_channels") val communicationElementsMaxChannels: Vector[Int],
    @key(
      "communication_elements_bit_per_sec_per_channel"
    ) val communicationElementsBitPerSecPerChannel: Vector[Double],
    @key("pre_computed_paths") val preComputedPaths: Map[String, Map[String, Iterable[String]]]
) extends DecisionModel
    with InstrumentedPlatformMixin[Double]
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try {
    java.util.Optional.of(write(this))
  } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try {
    java.util.Optional.of(writeBinary(this))
  } catch { case _ => java.util.Optional.empty() }

  // #covering_documentation_example
  override def part(): ju.Set[String] =
    ((processingElems ++ communicationElems ++ storageElems).toSet ++ (topologySrcs
      .zip(topologyDsts)
      .toSet)
      .map(_.toString)).asJava
  // #covering_documentation_example

  val platformElements: Vector[String] =
    processingElems ++ communicationElems ++ storageElems

  val topology = {
    // Graph.from(platformElements, topologySrcs.zip(topologyDsts).map((src, dst) => src ~> dst))
    val g = DefaultDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    platformElements.foreach(g.addVertex)
    topologySrcs.zip(topologyDsts).foreach((src, dst) => g.addEdge(src, dst))
    g
  }

  val computedPaths =
    platformElements
      .map(src =>
        src ->
          platformElements
            .map(dst =>
              dst -> {
                if (
                  preComputedPaths.contains(src) && preComputedPaths(src)
                    .contains(dst) && !preComputedPaths(src)(dst).isEmpty
                ) {
                  preComputedPaths(src)(dst)
                } else {
                  // topology
                  //   .get(src)
                  //   .withSubgraph(nodes =
                  //     v => v.value == src || v.value == dst || communicationElems.contains(v.value)
                  //   )
                  //   .shortestPathTo(topology.get(dst), e => 1)
                  //   .map(path => path.nodes.map(_.value.toString()))
                  //   .map(_.drop(1).dropRight(1))
                  //   .getOrElse(Seq.empty)
                  val subelements = platformElements
                    .filter(e => e == src || e == dst || communicationElems.contains(e))
                    .toSet
                    .asJava
                  val paths =
                    FloydWarshallShortestPaths(AsSubgraph(topology, subelements))
                  val path = paths.getPath(src, dst)
                  if (path != null) {
                    path.getVertexList.asScala.drop(1).dropRight(1)
                  } else {
                    Seq.empty
                  }
                }
              }
            )
            .toMap
      )
      .toMap

  val maxTraversalTimePerBit: Vector[Vector[Rational]] = {
    // val paths = FloydWarshallShortestPaths(directedAndConnectedMinTimeGraph)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        val f = computedPaths(src)(dst)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            (communicationElementsBitPerSecPerChannel(dstIdx) * communicationElementsMaxChannels(
              dstIdx
            ))
          })
          .foldLeft(Rational.zero)(_ + _)
        if (f == Rational.zero) then Rational.zero else f.reciprocal
      })
    })
  }

  val minTraversalTimePerBit: Vector[Vector[Rational]] = {
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        val f = computedPaths(src)(dst)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            (communicationElementsBitPerSecPerChannel(dstIdx))
          })
          .foldLeft(Rational.zero)(_ + _)
        if (f == Rational.zero) then Rational.zero else f.reciprocal
      })
    })
  }

  override def category() = "SharedMemoryMultiCore"

}
