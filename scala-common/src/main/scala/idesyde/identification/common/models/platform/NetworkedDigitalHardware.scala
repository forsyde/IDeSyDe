package idesyde.identification.forsyde.models.platform

import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import org.jgrapht.opt.graph.sparse.SparseIntUndirectedGraph
import org.jgrapht.alg.util.Pair
import org.jgrapht.opt.graph.sparse.SparseIntUndirectedWeightedGraph
import org.jgrapht.graph.AsWeightedGraph
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph
import org.jgrapht.graph.AsUndirectedGraph
import org.jgrapht.opt.graph.sparse.IncomingEdgesSupport
import org.jgrapht.graph.AsSubgraph
import idesyde.utils.MultipliableFractional
import spire.math.Rational
import spire.implicits.*
import idesyde.identification.DecisionModel
import idesyde.identification.common.StandardDecisionModel
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.Graph

final case class NetworkedDigitalHardware(
    val processingElems: Array[String],
    val communicationElems: Array[String],
    val storageElems: Array[String],
    val topology: Graph[String, DefaultEdge],
    val communicationElementsMaxChannels: Array[Int],
    val communicationElementsBitPerSecPerChannel: Array[Rational]
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

  // val topology = AsUndirectedGraph(topologyDirected)

  private lazy val routesProc2MemoryAlgorithm = AllDirectedPaths(topology)

  lazy val routesProc2Memory =
    processingElems.zipWithIndex.map((pe, src) =>
      storageElems.zipWithIndex.map((me, dst) =>
        val paths =
          routesProc2MemoryAlgorithm.getAllPaths(pe, me, true, communicationElems.length)
        paths.asScala
          .map(path =>
            path.getVertexList.subList(1, path.getLength - 1).asScala.toArray.map(_.toInt)
          )
          // this use the integer encoding to guarantee that all paths are made of communication elements
          .filter(path =>
            path.forall(v =>
              processingElems.length < v && v <= processingElems.length + communicationElems.length
            )
          )
          .toArray
      )
    )

  /** This graph is a weighted undirected graph where the weights are the bit/s a message can
    * experience when hopping from one element to another.
    */
  lazy val directedAndConnectedMinTimeGraph = AsWeightedGraph(topology, (e) => {
    val src = topology.getEdgeSource(e)
    val dst = topology.getEdgeTarget(e)
    val dstIdx = communicationElems.indexOf(dst) 
    if (dstIdx > -1) {
      (communicationElementsBitPerSecPerChannel(dstIdx) * communicationElementsMaxChannels(dstIdx)).toDouble
    } else 0.0
  }, true, false)

  lazy val directedAndConnectedMaxTimeGraph = AsWeightedGraph(topology, (e) => {
    val src = topology.getEdgeSource(e)
    val dst = topology.getEdgeTarget(e)
    val dstIdx = communicationElems.indexOf(dst) 
    if (dstIdx > -1) {
      (communicationElementsBitPerSecPerChannel(dstIdx) ).toDouble
    } else 0.0
  }, true, false)

  private val pathsAlgorithm = DijkstraManyToManyShortestPaths(topology)

  lazy val minTraversalTimePerBit: Array[Array[Rational]] = {
    val paths = FloydWarshallShortestPaths(directedAndConnectedMinTimeGraph)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        if (i == j) {
          Rational.zero
        } else if (paths.getPath(src, dst) != null) {
          Rational(paths.getPathWeight(src, dst))
        } else
          Rational(-1)
      })
    })
  }

  lazy val maxTraversalTimePerBit: Array[Array[Rational]] = {
    val maxWeight = directedAndConnectedMaxTimeGraph.edgeSet.stream
      .mapToDouble(e => directedAndConnectedMaxTimeGraph.getEdgeWeight(e))
      .max
      .orElse(0.0)
    val reversedGraph = AsWeightedGraph(
      directedAndConnectedMaxTimeGraph,
      (e) => maxWeight - directedAndConnectedMaxTimeGraph.getEdgeWeight(e),
      true,
      false
    )
    val paths = FloydWarshallShortestPaths(reversedGraph)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        if (i == j) {
          Rational.zero
        } else if (paths.getPath(src, dst) != null) {
          Rational((maxWeight * paths.getPath(src, dst).getLength().toDouble) - paths.getPathWeight(src, dst))
        } else
          Rational(-1)
      })
    })
  }

  override val uniqueIdentifier = "NetworkedDigitalHardware"

}

object NetworkedDigitalHardware:

end NetworkedDigitalHardware
