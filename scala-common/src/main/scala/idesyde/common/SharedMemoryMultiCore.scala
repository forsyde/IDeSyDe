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

final case class SharedMemoryMultiCore(
    val processingElems: Vector[String],
    val storageElems: Vector[String],
    val communicationElems: Vector[String],
    val topologySrcs: Vector[String],
    val topologyDsts: Vector[String],
    val processorsFrequency: Vector[Long],
    val processorsProvisions: Vector[Map[String, Map[String, Double]]],
    val storageSizes: Vector[Long],
    val communicationElementsMaxChannels: Vector[Int],
    val communicationElementsBitPerSecPerChannel: Vector[Double],
    val preComputedPaths: Map[String, Map[String, Iterable[String]]]
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

  override val category = "SharedMemoryMultiCore"

}
