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

  def coveredVertexes = processors ++ memories ++ networkInterfaces

  val platformElements: Array[String] =
    processors ++ memories ++ networkInterfaces ++ routers

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
            .getOrElse(Seq.empty)
        }
      )
    )

  def uniqueIdentifier: String = "TiledMultiCore"
}
