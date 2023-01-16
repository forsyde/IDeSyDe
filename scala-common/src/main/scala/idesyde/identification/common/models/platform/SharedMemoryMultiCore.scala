package idesyde.identification.common.models.platform

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import idesyde.utils.MultipliableFractional
import spire.math.Rational
import spire.implicits.*
import idesyde.identification.DecisionModel
import idesyde.identification.common.StandardDecisionModel
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._
import idesyde.identification.common.models.platform.InstrumentedPlatformMixin

final case class SharedMemoryMultiCore(
    val processingElems: Array[String],
    val storageElems: Array[String],
    val communicationElems: Array[String],
    val topologySrcs: Array[String],
    val topologyDsts: Array[String],
    val processorsFrequency: Array[Long],
    val processorsProvisions: Array[Map[String, Map[String, Rational]]],
    val storageSizes: Array[Long],
    val communicationElementsMaxChannels: Array[Int],
    val communicationElementsBitPerSecPerChannel: Array[Rational],
    val preComputedPaths: Map[String, Map[String, Iterable[String]]]
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Rational] {

  val coveredElements         = (processingElems ++ communicationElems ++ storageElems).toSet
  val coveredElementRelations = topologySrcs.zip(topologyDsts).toSet

  val platformElements: Array[String] =
    processingElems ++ communicationElems ++ storageElems

  val topology = Graph.from(platformElements, topologySrcs.zip(topologyDsts).map((src, dst) => src ~> dst))

  val computedPaths =
    platformElements.map(src => src ->
      platformElements.map(dst => dst -> {
          if (preComputedPaths.contains(src) && preComputedPaths(src).contains(dst) && !preComputedPaths(src)(dst).isEmpty) {
            preComputedPaths(src)(dst)
          } else {
            topology
              .get(src)
              .withSubgraph(nodes =
                v =>
                  v.value == src || v.value == dst || communicationElems.contains(v.value)
              )
              .shortestPathTo(topology.get(dst), e => 1)
              .map(path => path.nodes.map(_.value.toString()))
              .map(_.drop(1).dropRight(1))
              .getOrElse(Seq.empty)
          }
        }
      ).toMap
    ).toMap

  val maxTraversalTimePerBit: Array[Array[Rational]] = {
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

  val minTraversalTimePerBit: Array[Array[Rational]] = {
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

  override val uniqueIdentifier = "SharedMemoryMultiCore"

}
