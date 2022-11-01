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

final case class SharedMemoryMultiCore(
    val processingElems: Array[String],
    val communicationElems: Array[String],
    val storageElems: Array[String],
    val topologySrcs: Array[String],
    val topologyDsts: Array[String],
    val communicationElementsMaxChannels: Array[Int],
    val communicationElementsBitPerSecPerChannel: Array[Rational],
    val preComputedPaths: Array[Array[Iterable[String]]]
) extends StandardDecisionModel {

  val coveredVertexes = processingElems ++ communicationElems ++ storageElems

  val platformElements: Array[String] =
    processingElems ++ communicationElems ++ storageElems

  // val topologyDirected =
  //   if (linksSrcs.length > 0)
  //     SparseIntDirectedGraph(
  //       platformElements.length,
  //       (linksSrcs.zipWithIndex
  //         .map((src, i) =>
  //           Pair(
  //             platformElements.indexOf(src).asInstanceOf[Integer],
  //             platformElements.indexOf(linksDsts(i)).asInstanceOf[Integer]
  //           )
  //         ) ++ linksDsts.zipWithIndex
  //         .map((dst, i) =>
  //           Pair(
  //             platformElements.indexOf(dst).asInstanceOf[Integer],
  //             platformElements.indexOf(linksSrcs(i)).asInstanceOf[Integer]
  //           )
  //         )).toList.asJava,
  //       IncomingEdgesSupport.FULL_INCOMING_EDGES
  //     )
  //   else
  //     SimpleGraph
  //       .createBuilder[Integer, Integer](() => 0.asInstanceOf[Integer])
  //       .addVertices((0 until platformElements.length).map(_.asInstanceOf[Integer]).toArray: _*)
  //       .build

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
          // topology
          //   .get(pe)
          //   .withSubgraph(nodes =
          //     v =>
          //       v.toOuter == pe || v.toOuter == me || communicationElems.contains(
          //         v.toOuter.asInstanceOf[String]
          //       )
          //   )
          //   .pathTo(topology.get(me))
        }
      )
    )

  /** This graph is a weighted undirected graph where the weights are the bit/s a message can
    * experience when hopping from one element to another.
    */
  // val directedAndConnectedMinTimeGraph = Graph(
  //   topology.edges.map(e =>
  //     val dstIdx = communicationElems.indexOf(e.target.toOuter.toString())
  //     e.source ~> e.target % (communicationElementsBitPerSecPerChannel(
  //       dstIdx
  //     ) * communicationElementsMaxChannels(
  //       dstIdx
  //     ))
  //   )
  // )
  // AsWeightedGraph(
  //   topology,
  //   (e) => {
  //     val src    = topology.getEdgeSource(e)
  //     val dst    = topology.getEdgeTarget(e)
  //     val dstIdx = communicationElems.indexOf(dst)
  //     if (dstIdx > -1) {
  //       (communicationElementsBitPerSecPerChannel(dstIdx) * communicationElementsMaxChannels(
  //         dstIdx
  //       )).toDouble
  //     } else 0.0
  //   },
  //   true,
  //   false
  // )

  // val directedAndConnectedMaxTimeGraph = Graph(
  //   topology.edges.map(e =>
  //     val dstIdx = communicationElems.indexOf(e.target.toOuter)
  //     e.source.toOuter ~> e.target.toOuter % (communicationElementsBitPerSecPerChannel(
  //       dstIdx
  //     ))
  //   )
  // )
  // AsWeightedGraph(
  //   topology,
  //   (e) => {
  //     val src    = topology.getEdgeSource(e)
  //     val dst    = topology.getEdgeTarget(e)
  //     val dstIdx = communicationElems.indexOf(dst)
  //     if (dstIdx > -1) {
  //       (communicationElementsBitPerSecPerChannel(dstIdx)).toDouble
  //     } else 0.0
  //   },
  //   true,
  //   false
  // )

  // private val pathsAlgorithm = DijkstraManyToManyShortestPaths(topology)

  val minTraversalTimePerBit: Array[Array[Rational]] = {
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

  val maxTraversalTimePerBit: Array[Array[Rational]] = {
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
