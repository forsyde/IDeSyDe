package idesyde.identification.common.models.platform

import math.Ordering.Implicits.infixOrderingOps
import math.Fractional.Implicits.infixFractionalOps

import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import scala.reflect.ClassTag
import org.jgrapht.graph.AsWeightedGraph
import scala.jdk.CollectionConverters._
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph
import org.jgrapht.alg.util.Pair
import java.util.stream.Collectors
import scala.collection.mutable.Buffer
import java.util.concurrent.Executors
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths
import java.util.concurrent.ThreadPoolExecutor
import idesyde.utils.CoreUtils.wfor
import spire.math._
import spire.implicits._

/** This mixin contains methods and logic that can aid platform models that are behave like tiled
  * multi core platforms. This can include for example NoC multicore architectures commonly used for
  * dataflow mapping and scheduling problems.
  */
trait TiledMultiCorePlatformMixin[MemT, TimeT](using fTimeT: Fractional[TimeT])(using
    conversion: Conversion[Double, TimeT]
)(using ClassTag[TimeT]) {

  def tileSet: Array[Int]
  def routerSet: Array[Int]

  def architectureGraph: Graph[Int, DefaultEdge]

  /** although generic, this function is required to return the correct timing _only_ between
    * adjacent routers, and not between any 2 routers. Non adjancent routers can return any value.
    * The correct general timing is later computed by the Mixin.
    */
  def architectureGraphMinimumHopTime(src: Int, dst: Int): TimeT

  /** although generic, this function is required to return the correct timing _only_ between
    * adjacent routers, and not between any 2 routers. Non adjancent routers can return any value.
    * The correct general timing is later computed by the Mixin.
    */
  def architectureGraphMaximumHopTime(src: Int, dst: Int): TimeT

  def maxMemoryPerTile: Array[MemT]
  def minTraversalTimePerBitPerRouter: Array[TimeT]
  def maxTraversalTimePerBitPerRouter: Array[TimeT]

  def maxTimePerInstructionPerTilePerMode: Array[Map[String, Map[String, TimeT]]]

  def numProcessors = tileSet.size
  def numRouters    = routerSet.size
  def platformSet   = tileSet ++ routerSet

  private def directedAndConnectedMinTimeGraph: Graph[Int, DefaultEdge] = {
    val gBuilder = SimpleDirectedWeightedGraph.createBuilder[Int, DefaultEdge](classOf[DefaultEdge])
    architectureGraph
      .edgeSet()
      .forEach(e => {
        val src    = architectureGraph.getEdgeSource(e)
        val dst    = architectureGraph.getEdgeTarget(e)
        val srcIdx = routerSet.indexOf(src)
        val dstIdx = routerSet.indexOf(dst)
        if (routerSet.contains(dst)) {
          gBuilder.addEdge(
            src,
            dst,
            architectureGraphMinimumHopTime(
              srcIdx,
              dstIdx
            ).toDouble + minTraversalTimePerBitPerRouter(
              dstIdx
            ).toDouble
          )
        } else if (tileSet.contains(dst)) {
          gBuilder.addEdge(src, dst, architectureGraphMinimumHopTime(srcIdx, dstIdx).toDouble)
        }
        if (routerSet.contains(src)) {
          gBuilder.addEdge(
            dst,
            src,
            architectureGraphMinimumHopTime(
              dstIdx,
              srcIdx
            ).toDouble + minTraversalTimePerBitPerRouter(
              srcIdx
            ).toDouble
          )
        } else if (tileSet.contains(src)) {
          gBuilder.addEdge(dst, src, architectureGraphMinimumHopTime(dstIdx, srcIdx).toDouble)
        }
      })
    gBuilder.buildAsUnmodifiable()
  }

  private def directedAndConnectedMaxTimeGraph: Graph[Int, DefaultEdge] = {
    val gBuilder = SimpleDirectedWeightedGraph.createBuilder[Int, DefaultEdge](classOf[DefaultEdge])
    architectureGraph
      .edgeSet()
      .forEach(e => {
        val src    = architectureGraph.getEdgeSource(e)
        val dst    = architectureGraph.getEdgeTarget(e)
        val srcIdx = routerSet.indexOf(src)
        val dstIdx = routerSet.indexOf(dst)
        if (routerSet.contains(dst)) {
          gBuilder.addEdge(
            src,
            dst,
            architectureGraphMaximumHopTime(
              srcIdx,
              dstIdx
            ).toDouble + maxTraversalTimePerBitPerRouter(
              dstIdx
            ).toDouble
          )
        } else if (tileSet.contains(dst)) {
          gBuilder.addEdge(src, dst, architectureGraphMaximumHopTime(srcIdx, dstIdx).toDouble)
        }
        if (routerSet.contains(src)) {
          gBuilder.addEdge(
            dst,
            src,
            architectureGraphMaximumHopTime(
              dstIdx,
              srcIdx
            ).toDouble + maxTraversalTimePerBitPerRouter(
              srcIdx
            ).toDouble
          )
        } else if (tileSet.contains(src)) {
          gBuilder.addEdge(dst, src, architectureGraphMaximumHopTime(dstIdx, srcIdx).toDouble)
        }
      })
    gBuilder.buildAsUnmodifiable()
  }

  def minTraversalTimePerBit: Array[Array[TimeT]] = {
    val paths = FloydWarshallShortestPaths(directedAndConnectedMinTimeGraph)
    platformSet.zipWithIndex.map((src, i) => {
      platformSet.zipWithIndex.map((dst, j) => {
        if (i == j) {
          fTimeT.zero
        } else if (paths.getPath(src, dst) != null) {
          conversion(paths.getPathWeight(src, dst))
        } else
          fTimeT.zero - fTimeT.one
      })
    })
  }

  def maxTraversalTimePerBit: Array[Array[TimeT]] = {
    val maxGraph = directedAndConnectedMaxTimeGraph
    val maxWeight = maxGraph.edgeSet.stream
      .mapToDouble(e => maxGraph.getEdgeWeight(e))
      .max
      .orElse(0.0)
    val reversedGraph = AsWeightedGraph(
      maxGraph,
      (e) => maxWeight - maxGraph.getEdgeWeight(e),
      true,
      false
    )
    val paths = FloydWarshallShortestPaths(reversedGraph)
    platformSet.zipWithIndex.map((src, i) => {
      platformSet.zipWithIndex.map((dst, j) => {
        if (i == j) {
          fTimeT.zero
        } else if (paths.getPath(src, dst) != null) {
          conversion(
            (maxWeight * paths.getPath(src, dst).getLength().toDouble) - paths.getPathWeight(
              src,
              dst
            )
          )
        } else
          fTimeT.zero - fTimeT.one
      })
    })
  }

  def computeRouterPaths: Array[Array[Array[Int]]] = {
    // val executor = Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors() - 10, 1)).asInstanceOf[ThreadPoolExecutor]
    val results = tileSet.map(src => tileSet.map(dst => Buffer.empty[Int]))
    val sparseGraph = SparseIntDirectedGraph(
      architectureGraph.vertexSet().size(),
      architectureGraph
        .edgeSet()
        .stream()
        .map(e =>
          Pair.of(
            platformSet.indexOf(architectureGraph.getEdgeSource(e)).asInstanceOf[Integer],
            platformSet.indexOf(architectureGraph.getEdgeTarget(e)).asInstanceOf[Integer]
          )
        )
        .collect(Collectors.toList())
    )
    val tileSetIdxs = tileSet.map(tile => platformSet.indexOf(tile).asInstanceOf[Integer]).toSet
    val paths       = FloydWarshallShortestPaths(sparseGraph)
    // val paths = pathAlg.getManyToManyPaths(tileSetIdxs.asJava, tileSetIdxs.asJava)
    wfor(0, _ < tileSet.size, _ + 1) { srcIdx =>
      val src = platformSet.indexOf(srcIdx)
      wfor(0, _ < tileSet.size, _ + 1) { dstIdx =>
        val dst = platformSet.indexOf(dstIdx)
        if (src != dst && paths.getPath(src, dst) != null) {
          val path = paths.getPath(src, dst).getVertexList()
          wfor(1, _ < path.size() - 1, _ + 1) { vIdx =>
            results(src)(dst) += platformSet(path.get(vIdx))
          }
        }
      }
    }
    // tileSetIdxs.foreach(src =>
    //   tileSetIdxs.foreach(dst => {
    //     if (src != dst && paths.getPath(src, dst) != null) {
    //       val p = paths.getPath(src, dst)
    //       p.getVertexList()
    //         .subList(1, p.getLength())
    //         .forEach(vIdx => {
    //           results(src)(dst) += platformSet(vIdx)
    //         })
    //       // Option(paths.getPath(src, dst)).map(p =>
    //       //   // println(src +" -> " + dst + ": " + p.getVertexList().asScala.tail.drop(1).toArray.mkString(", "))
    //       //   p.getVertexList().asScala.map(vIdx => platformSet(vIdx)).drop(1).dropRight(1).toArray).getOrElse(Array.emptyIntArray)
    //     } // else  Array.emptyIntArray
    //   })
    // )
    results.map(_.map(_.toArray))
  }

}
