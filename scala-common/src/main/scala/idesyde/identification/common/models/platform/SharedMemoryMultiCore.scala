package idesyde.identification.forsyde.models.platform

import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}

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
import idesyde.identification.models.platform.InstrumentedPlatformMixin

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
    val preComputedPaths: Array[Array[Iterable[String]]]
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Rational] {

  val coveredVertexes = processingElems ++ communicationElems ++ storageElems

  val platformElements: Array[String] =
    processingElems ++ communicationElems ++ storageElems

  val topology = Graph(topologySrcs.zip(topologyDsts).map((src, dst) => src ~> dst): _*)

  val computedPaths =
    platformElements.zipWithIndex.map((pe, src) =>
      platformElements.zipWithIndex.map((me, dst) =>
        if (!preComputedPaths(src)(dst).isEmpty) {
          preComputedPaths(src)(dst)
        } else {
          topology
            .get(pe)
            .withSubgraph(nodes =
              v =>
                v.toOuter == pe || v.toOuter == me || communicationElems.contains(
                  v.value.toString()
                )
            )
            .shortestPathTo(topology.get(me), e => 1)
            .map(path => path.nodes.map(_.value.toString()))
            .getOrElse(Seq.empty)
        }
      )
    )

  val maxTraversalTimePerBit: Array[Array[Rational]] = {
    // val paths = FloydWarshallShortestPaths(directedAndConnectedMinTimeGraph)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        val path = topology
          .get(src)
          .shortestPathTo(
            topology.get(dst),
            e => {
              val dstIdx = communicationElems.indexOf(e.target.toOuter.toString())
              (communicationElementsBitPerSecPerChannel(dstIdx) * communicationElementsMaxChannels(
                dstIdx
              )).toDouble
            }
          )
        if (i == j) {
          Rational.zero
        } else {
          path match {
            case Some(p) => p.weight
            case _       => Rational(-1)
          }
        }
      })
    })
  }

  val minTraversalTimePerBit: Array[Array[Rational]] = {
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        val path = topology
          .get(src)
          .shortestPathTo(
            topology.get(dst),
            e => {
              val dstIdx = communicationElems.indexOf(e.target.toOuter.toString())
              (communicationElementsBitPerSecPerChannel(dstIdx)).toDouble
            }
          )
        if (i == j) {
          Rational.zero
        } else {
          path match {
            case Some(p) => p.weight
            case _       => Rational(-1)
          }
        }
      })
    })
  }

  override val uniqueIdentifier = "SharedMemoryMultiCore"

}

object SharedMemoryMultiCore {}
