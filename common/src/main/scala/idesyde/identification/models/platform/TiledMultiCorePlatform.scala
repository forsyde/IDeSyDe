package idesyde.identification.models.platform

import math.Ordering.Implicits.infixOrderingOps
import math.Fractional.Implicits.infixFractionalOps

import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import scala.reflect.ClassTag
import org.jgrapht.graph.AsWeightedGraph

/** This mixin contains methods and logic that can aid platform models that are behave like tiled
  * multi core platforms. This can include for example NoC multicore architectures commonly used
  * for dataflow mapping and scheduling problems.
  */
trait TiledMultiCorePlatformMixin[MemT, TimeT](using fTimeT: Fractional[TimeT])(using
    conversion: Conversion[Double, TimeT]
)(using ClassTag[TimeT]) {

  def processorSet: Array[Int]
  def routerSet: Array[Int]

  def architectureGraph: Graph[Int, DefaultEdge]

  def architectureGraphMinimumHopTime(src: Int, dst: Int): TimeT
  def architectureGraphMaximumHopTime(src: Int, dst: Int): TimeT

  def maxMemoryPerTile: Array[MemT]
  def minTraversalTimePerBitPerRouter: Array[TimeT]
  def maxTraversalTimePerBitPerRouter: Array[TimeT]

  def maxTimePerInstructionPerTile: Array[Map[String, TimeT]]

  def numProcessors = processorSet.size
  def numRouters    = routerSet.size
  def platformSet   = processorSet ++ routerSet

  private def directedAndConnectedMinTimeGraph: Graph[Int, DefaultEdge] = {
    val gBuilder = SimpleDirectedWeightedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    architectureGraph
      .edgeSet()
      .forEach(e => {
        val src = architectureGraph.getEdgeSource(e)
        val dst = architectureGraph.getEdgeTarget(e)
        if (routerSet.contains(dst)) {
          gBuilder.addEdge(
            src,
            dst,
            architectureGraphMinimumHopTime(src, dst).toDouble + minTraversalTimePerBitPerRouter(
              dst
            ).toDouble
          )
        } else if (processorSet.contains(dst)) {
          gBuilder.addEdge(src, dst, architectureGraphMinimumHopTime(src, dst).toDouble)
        }
        if (routerSet.contains(src)) {
          gBuilder.addEdge(
            dst,
            src,
            architectureGraphMinimumHopTime(dst, src).toDouble + minTraversalTimePerBitPerRouter(
              src
            ).toDouble
          )
        } else if (processorSet.contains(src)) {
          gBuilder.addEdge(dst, src, architectureGraphMinimumHopTime(dst, src).toDouble)
        }
      })
    gBuilder.buildAsUnmodifiable()
  }

  private def directedAndConnectedMaxTimeGraph: Graph[Int, DefaultEdge] = {
    val gBuilder = SimpleDirectedWeightedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    architectureGraph
      .edgeSet()
      .forEach(e => {
        val src = architectureGraph.getEdgeSource(e)
        val dst = architectureGraph.getEdgeTarget(e)
        if (routerSet.contains(dst)) {
          gBuilder.addEdge(
            src,
            dst,
            architectureGraphMaximumHopTime(src, dst).toDouble + maxTraversalTimePerBitPerRouter(
              dst
            ).toDouble
          )
        } else if (processorSet.contains(dst)) {
          gBuilder.addEdge(src, dst, architectureGraphMaximumHopTime(src, dst).toDouble)
        }
        if (routerSet.contains(src)) {
          gBuilder.addEdge(
            dst,
            src,
            architectureGraphMaximumHopTime(dst, src).toDouble + maxTraversalTimePerBitPerRouter(
              src
            ).toDouble
          )
        } else if (processorSet.contains(src)) {
          gBuilder.addEdge(dst, src, architectureGraphMaximumHopTime(dst, src).toDouble)
        }
      })
    gBuilder.buildAsUnmodifiable()
  }

  def minTraversalTimePerBit: Array[Array[TimeT]] = {
    val paths = DijkstraManyToManyShortestPaths(directedAndConnectedMinTimeGraph)
    platformSet.zipWithIndex.map((src, i) => {
      platformSet.zipWithIndex.map((dst, j) => {
        if (i != j && paths.getPath(i, j) != null) {
          conversion(paths.getPathWeight(i, j))
        } else
          fTimeT.zero - fTimeT.one
      })
    })
  }

  def maxTraversalTimePerBit: Array[Array[TimeT]] = {
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
    val paths = DijkstraManyToManyShortestPaths(reversedGraph)
    platformSet.zipWithIndex.map((src, i) => {
      platformSet.zipWithIndex.map((dst, j) => {
        if (i != j && paths.getPath(i, j) != null) {
          conversion(maxWeight - paths.getPathWeight(i, j))
        } else
          fTimeT.zero - fTimeT.one
      })
    })
  }

}
