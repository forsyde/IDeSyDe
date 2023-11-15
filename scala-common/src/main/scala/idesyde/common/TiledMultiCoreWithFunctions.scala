package idesyde.common

import scala.jdk.CollectionConverters._

import upickle.default.*

import idesyde.common.InstrumentedPlatformMixin
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import idesyde.core.DecisionModel
import java.{util => ju}

final case class TiledMultiCoreWithFunctions(
    val processors: Vector[String],
    val memories: Vector[String],
    val networkInterfaces: Vector[String],
    val routers: Vector[String],
    val interconnectTopologySrcs: Vector[String],
    val interconnectTopologyDsts: Vector[String],
    val processorsProvisions: Vector[Map[String, Map[String, Double]]],
    val processorsFrequency: Vector[Long],
    val tileMemorySizes: Vector[Long],
    val communicationElementsMaxChannels: Vector[Int],
    val communicationElementsBitPerSecPerChannel: Vector[Double],
    val preComputedPaths: Map[String, Map[String, Iterable[String]]]
) extends DecisionModel
    with InstrumentedPlatformMixin[Double]
    derives ReadWriter {

  override def part(): ju.Set[String] =
    ((processors ++ memories ++ networkInterfaces ++ routers).toSet ++ (interconnectTopologySrcs
      .zip(interconnectTopologyDsts)
      .toSet)
      .map(_.toString)).asJava

  val communicationElems = networkInterfaces ++ routers

  val platformElements: Vector[String] =
    processors ++ memories ++ communicationElems

  val topology = Graph.from(
    platformElements,
    interconnectTopologySrcs.zip(interconnectTopologyDsts).map((src, dst) => src ~> dst) ++
      processors.zip(memories).map((src, dst) => src ~> dst) ++ processors
        .zip(memories)
        .map((src, dst) => dst ~> src) ++
      processors.zip(networkInterfaces).map((src, dst) => src ~> dst) ++ processors
        .zip(networkInterfaces)
        .map((src, dst) => dst ~> src)
  )

  val computedPaths =
    platformElements.map(src =>
      platformElements.map(dst =>
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
      )
    )

  val maxTraversalTimePerBit: Vector[Vector[Double]] = {
    // val paths = FloydWarshallShortestPaths(directedAndConnectedMinTimeGraph)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        computedPaths(i)(j)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            1.0 / communicationElementsBitPerSecPerChannel(dstIdx)
          })
          .foldLeft(0.0)(_ + _)
      })
    })
  }

  val minTraversalTimePerBit: Vector[Vector[Double]] = {
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        computedPaths(i)(j)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            1.0 / communicationElementsBitPerSecPerChannel(
              dstIdx
            ) / communicationElementsMaxChannels(
              dstIdx
            )
          })
          .foldLeft(0.0)(_ + _)
      })
    })
  }

  val symmetricTileGroups: Set[Set[String]] = {
    val wccts = maxTraversalTimePerBit
    val outgoingWCCThistograms =
      wccts.map(dsts => dsts.groupBy(t => t).map((k, v) => k -> v.length))
    val incomingWCCThistograms =
      platformElements.zipWithIndex.map((dst, i) =>
        platformElements.zipWithIndex
          .map((src, j) => wccts(j)(i))
          .groupBy(t => t)
          .map((k, v) => k -> v.length)
      )
    var groups      = Set[Set[String]]()
    var toBeMatched = Set(processors: _*)
    while (!toBeMatched.isEmpty) {
      val t = toBeMatched.head
      val otherSymmetric = toBeMatched.tail
        .filter(tt => {
          val tIdx  = platformElements.indexOf(t)
          val ttIdx = platformElements.indexOf(tt)
          processorsProvisions(tIdx) == processorsProvisions(ttIdx) &&
          outgoingWCCThistograms(tIdx) == outgoingWCCThistograms(ttIdx) &&
          incomingWCCThistograms(tIdx) == incomingWCCThistograms(ttIdx)
        })
      toBeMatched -= t
      toBeMatched --= otherSymmetric
      groups += (otherSymmetric + t)
    }
    groups.toSet
  }

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def category(): String = "TiledMultiCoreWithFunctions"
}
