package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational
import idesyde.identification.models.platform.InstrumentedPlatformMixin
import scalax.collection.Graph
import scalax.collection.GraphPredef._

final case class TiledMultiCore(
    val processors: Array[String],
    val memories: Array[String],
    val networkInterfaces: Array[String],
    val routers: Array[String],
    val interconnectTopologySrcs: Array[String],
    val interconnectTopologyDsts: Array[String],
    val processorsProvisions: Array[Map[String, Map[String, Rational]]],
    val processorsFrequency: Array[Long],
    val tileMemorySizes: Array[Long],
    val communicationElementsMaxChannels: Array[Int],
    val communicationElementsBitPerSecPerChannel: Array[Rational],
    val preComputedPaths: Array[Array[Iterable[String]]]
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Rational] {

  val coveredElements         = processors ++ memories ++ networkInterfaces ++ routers
  val coveredElementRelations = interconnectTopologySrcs.zip(interconnectTopologyDsts)

  val communicationElems = networkInterfaces ++ routers

  val platformElements: Array[String] =
    processors ++ memories ++ communicationElems

  val topology = Graph(
    interconnectTopologySrcs.zip(interconnectTopologyDsts).map((src, dst) => src ~> dst): _*
  )

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
                v.toOuter == pe || v.toOuter == me || networkInterfaces.contains(
                  v.value.toString()
                ) || routers.contains(v.value.toString())
            )
            .shortestPathTo(topology.get(me), e => 1)
            .map(path => path.nodes.map(_.value.toString()))
            .map(_.drop(1).dropRight(1))
            .getOrElse(Seq.empty)
        }
      )
    )

  val maxTraversalTimePerBit: Array[Array[Rational]] = {
    // val paths = FloydWarshallShortestPaths(directedAndConnectedMinTimeGraph)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        computedPaths(i)(j)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            (communicationElementsBitPerSecPerChannel(dstIdx) * communicationElementsMaxChannels(
              dstIdx
            ))
          })
          .reduce(_ + _)
      })
    })
  }

  val minTraversalTimePerBit: Array[Array[Rational]] = {
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        computedPaths(i)(j)
          .map(ce => {
            val dstIdx = communicationElems.indexOf(ce)
            communicationElementsBitPerSecPerChannel(dstIdx)
          })
          .reduce(_ + _)
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

  def uniqueIdentifier: String = "TiledMultiCore"
}
