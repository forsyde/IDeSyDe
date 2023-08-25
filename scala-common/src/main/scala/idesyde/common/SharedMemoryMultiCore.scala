package idesyde.common

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import spire.math.Rational
import spire.implicits.*
import idesyde.core.DecisionModel
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._
import idesyde.common.InstrumentedPlatformMixin
import idesyde.core.CompleteDecisionModel
import upickle.default._
import upickle.implicits.key

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
) extends StandardDecisionModel
    with CompleteDecisionModel
    with InstrumentedPlatformMixin[Double]
    derives ReadWriter {

  override def bodyAsText: String = write(this)

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

  // #covering_documentation_example
  val coveredElements =
    (processingElems ++ communicationElems ++ storageElems).toSet ++ (topologySrcs
      .zip(topologyDsts)
      .toSet)
      .map(_.toString)
  // #covering_documentation_example

  val platformElements: Vector[String] =
    processingElems ++ communicationElems ++ storageElems

  val topology =
    Graph.from(platformElements, topologySrcs.zip(topologyDsts).map((src, dst) => src ~> dst))

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
                  topology
                    .get(src)
                    .withSubgraph(nodes =
                      v => v.value == src || v.value == dst || communicationElems.contains(v.value)
                    )
                    .shortestPathTo(topology.get(dst), e => 1)
                    .map(path => path.nodes.map(_.value.toString()))
                    .map(_.drop(1).dropRight(1))
                    .getOrElse(Seq.empty)
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
        computedPaths(src)(dst)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            (communicationElementsBitPerSecPerChannel(dstIdx) * communicationElementsMaxChannels(
              dstIdx
            ))
          })
          .foldLeft(Rational.zero)(_ + _)
      })
    })
  }

  val minTraversalTimePerBit: Vector[Vector[Rational]] = {
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        computedPaths(src)(dst)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            (communicationElementsBitPerSecPerChannel(dstIdx))
          })
          .foldLeft(Rational.zero)(_ + _)
      })
    })
  }

  override val category = "MemoryMappableMultiCore"

}
