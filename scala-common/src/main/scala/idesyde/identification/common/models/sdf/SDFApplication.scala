package idesyde.identification.common.models.sdf

import scala.jdk.CollectionConverters.*

import idesyde.utils.SDFUtils
import idesyde.identification.common.models.workload.ParametricRateDataflowWorkloadMixin
import idesyde.identification.common.models.workload.InstrumentedWorkloadMixin
import scala.collection.mutable
import java.util.stream.Collectors
import spire.math.*
import idesyde.identification.common.StandardDecisionModel
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.edge.Implicits._
import scala.collection.mutable.Buffer

final case class SDFApplication(
    val actorsIdentifiers: Vector[String],
    val channelsIdentifiers: Vector[String],
    val topologySrcs: Vector[String],
    val topologyDsts: Vector[String],
    val topologyEdgeValue: Vector[Int],
    val actorSizes: Vector[Long],
    val actorComputationalNeeds: Vector[Map[String, Map[String, Long]]],
    val channelNumInitialTokens: Vector[Int],
    val channelTokenSizes: Vector[Long],
    val actorThrouhgputs: Vector[Double]
) extends StandardDecisionModel
    with ParametricRateDataflowWorkloadMixin
    with InstrumentedWorkloadMixin {

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size
  val coveredElements         = (actorsIdentifiers ++ channelsIdentifiers).toSet
  val coveredElementRelations = topologySrcs.zip(topologyDsts).toSet


  val topology = Graph.from(
    actorsIdentifiers ++ channelsIdentifiers,
    topologySrcs
      .zip(topologyDsts)
      .zipWithIndex
      .map((srcdst, i) => srcdst._1 ~> srcdst._2 % topologyEdgeValue(i))
  )

  def isSelfConcurrent(actor: String): Boolean = channelsIdentifiers.exists(c =>
    topology.get(c).diSuccessors.exists(dst => dst.toOuter == actor) &&
      topology.get(c).diPredecessors.exists(src => src.toOuter == actor)
  )

  val dataflowGraphs = Vector(
    topologySrcs
      .zip(topologyDsts)
      .zipWithIndex
      .map((srcdst, i) => (srcdst._1, srcdst._2, topologyEdgeValue(i)))
  )

  val configurations = Vector((0, 0, "root"))

  val processComputationalNeeds = actorComputationalNeeds

  val processSizes = actorSizes

  val messagesFromChannels = dataflowGraphs.zipWithIndex.map((df, dfi) => {
    var lumpedChannels = mutable
      .Map[(String, String), (Vector[String], Long, Int, Int, Int)]()
      .withDefaultValue(
        (
          Vector(),
          0L,
          0,
          0,
          0
        )
      )
    for ((c, ci) <- channelsIdentifiers.zipWithIndex) {
      val thisInitialTokens = channelNumInitialTokens(ci)
      for (
        (src, _, produced) <- df.filter((s, d, _) => d == c);
        (_, dst, consumed) <- df.filter((s, d, _) => s == c)
      ) {
        val srcIdx             = actorsIdentifiers.indexOf(src)
        val dstIdex            = actorsIdentifiers.indexOf(dst)
        val sent               = produced * channelTokenSizes(ci)
        val (cs, d, p, q, tok) = lumpedChannels((src, dst))
        lumpedChannels((src, dst)) = (
          cs :+ c,
          d + sent,
          p + produced,
          q + consumed,
          tok + thisInitialTokens
        )
      }
    }
    lumpedChannels.map((k, v) => (k._1, k._2, v._1, v._2, v._3, v._4, v._5)).toVector
  })

  /** This abstracts the many sdf channels in the sdf multigraph into the form commonly presented in
    * papers and texts: with just a channel between every two actors.
    *
    * Every tuple in this is given by: (src actors index, dst actors index, lumped SDF channels,
    * size of message, produced, consumed, initial tokens)
    */
  val sdfMessages = messagesFromChannels(0)

  /** this is a simple shortcut for the balance matrix (originally called topology matrix) as SDFs
    * have only one configuration
    */
  val sdfBalanceMatrix: Vector[Vector[Int]] = computeBalanceMatrices(0)

  /** this is a simple shortcut for the repetition vectors as SDFs have only one configuration */
  val repetitionVectors = computeRepetitionVectors
  val sdfRepetitionVectors: Vector[Int] = repetitionVectors(0)

  val sdfDisjointComponents = disjointComponents.head

  val sdfPessimisticTokensPerChannel = pessimisticTokensPerChannel(repetitionVectors)

  val messagesMaxSizes: Vector[Long] =
    channelsIdentifiers.zipWithIndex.map((c, i) =>
      sdfPessimisticTokensPerChannel(i) * channelTokenSizes(i)
    )

  def isConsistent: Boolean = sdfRepetitionVectors.size > 0

  /** This graph serves the same purpose as the common HSDF transformation, but simply stores
    * precedences between firings instead of data movement.
    */
  lazy val firingsPrecedenceGraph = {
    // val firings = sdfRepetitionVectors.zipWithIndex.map((a, q) => (1 to q).map(qa => (a, qa)))
    var edges = Buffer[((String, Int), (String, Int))]()
    for ((s, d, _, _, produced, consumed, tokens) <- sdfMessages) {
      val src = actorsIdentifiers.indexOf(s)
      val dst = actorsIdentifiers.indexOf(d)
      // println((produced, consumed, tokens))
      // val src = vec.indexWhere(_ > 0)
      // val dst = vec.indexWhere(_ < 0)
      var qSrc = 0
      var qDst = 0
      while (qSrc <= sdfRepetitionVectors(src) && qDst <= sdfRepetitionVectors(dst)) {
        if (produced * qSrc + tokens - consumed * (qDst + 1) < 0) {
          qSrc += 1
        } else {
          qDst += 1
          if (qSrc > 0) {
            edges +:= ((s, qSrc), (d, qDst))
          }
        }
      }
    }
    for ((a, ai) <- actorsIdentifiers.zipWithIndex; q <- 1 to sdfRepetitionVectors(ai) - 1) {
      edges +:= ((a, q), (a, q + 1))
    }
    val param = edges.map((s, t) => (s ~> t))
    val nodes = edges.map((s, t) => s).toSet ++ edges.map((s, t) => t).toSet
    scalax.collection.Graph.from(nodes, param)
  }

  /** Same as [[firingsPrecedenceGraph]], but with one more firings per actors of the next periodic
    * phase
    */
  lazy val firingsPrecedenceWithExtraStepGraph = {
    var edges = Buffer[((String, Int), (String, Int))]()
    for ((s, d, _, _, produced, consumed, tokens) <- sdfMessages) {
      val src = actorsIdentifiers.indexOf(s)
      val dst = actorsIdentifiers.indexOf(d)
      // println((produced, consumed, tokens))
      // val src = vec.indexWhere(_ > 0)
      // val dst = vec.indexWhere(_ < 0)
      var qSrc = sdfRepetitionVectors(src)
      var qDst = sdfRepetitionVectors(dst)
      while (qSrc <= sdfRepetitionVectors(src) + 1 && qDst <= sdfRepetitionVectors(dst) + 1) {
        if (produced * qSrc + tokens - consumed * (qDst + 1) < 0) {
          qSrc += 1
        } else {
          qDst += 1
          if (qSrc > 0) {
            edges +:= ((s, qSrc), (d, qDst))
          }
        }
      }
      // the last jobs always communicate
      // edges +:= ((src, qSrc), (dst, qDst))
      // for (
      //   qDst <- 1 to sdfRepetitionVectors(dst);
      //   qSrcFrac = Rational(consumed * qDst - tokens, produced);
      //   qSrc <- qSrcFrac.floor.toInt to qSrcFrac.ceil.toInt
      //   if qSrc > 0
      // ) {
      //   edges +:= ((src, qSrc.toInt), (dst, qDst))
      // }
    }
    for (a <- actorsIdentifiers; i = actorsIdentifiers.indexOf(a)) {
      edges +:= ((a, sdfRepetitionVectors(i)), (a, sdfRepetitionVectors(i) + 1))
    }
    val param = edges.map((s, t) => (s ~> t)).toArray
    firingsPrecedenceGraph ++ param
  }

  lazy val decreasingActorConsumptionOrder = actorsIdentifiers.zipWithIndex
    .sortBy((a, ai) => {
      sdfBalanceMatrix.zipWithIndex
        .filter((vec, c) => vec(ai) < 0)
        .map((vec, c) => -channelTokenSizes(c) * vec(ai))
        .sum
    })
    .map((a, ai) => a)
    .reverse

  lazy val topologicalAndHeavyJobOrdering = firingsPrecedenceGraph
    .topologicalSort()
    .fold(
      cycleNode => {
        println("CYCLE NODES DETECTED")
        firingsPrecedenceGraph.nodes.map(_.value).toArray
      },
      topo =>
        topo
          .withLayerOrdering(
            firingsPrecedenceGraph.NodeOrdering((v1, v2) =>
              decreasingActorConsumptionOrder
                .indexOf(
                  v1.value._1
                ) - decreasingActorConsumptionOrder.indexOf(v2.value._1)
            )
          )
          .map(_.value)
          .toArray
    )

  lazy val topologicalAndHeavyJobOrderingWithExtra = firingsPrecedenceWithExtraStepGraph
    .topologicalSort()
    .fold(
      cycleNode => {
        println("CYCLE NODES DETECTED")
        firingsPrecedenceGraph.nodes.map(_.value).toArray
      },
      topo =>
        topo
          .withLayerOrdering(
            firingsPrecedenceWithExtraStepGraph.NodeOrdering((v1, v2) =>
              decreasingActorConsumptionOrder
                .indexOf(
                  v1.value._1
                ) - decreasingActorConsumptionOrder.indexOf(v2.value._1)
            )
          )
          .map(_.value)
          .toArray
    )

  lazy val topologicalAndHeavyActorOrdering =
    actorsIdentifiers.sortBy(a => topologicalAndHeavyJobOrdering.indexWhere((aa, _) => a == aa))

  lazy val topologicalAndHeavyActorOrderingWithExtra =
    actorsIdentifiers.sortBy(a =>
      topologicalAndHeavyJobOrderingWithExtra.indexWhere((aa, _) => a == aa)
    )

  override val uniqueIdentifier = "SDFApplication"

}
