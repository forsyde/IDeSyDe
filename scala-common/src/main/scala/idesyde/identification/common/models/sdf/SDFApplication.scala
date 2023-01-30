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

/** Decision model for synchronous dataflow graphs.
  *
  * This decision model encodes a synchronous dataflow graphs without its explicit topology matrix,
  * also known as balance matrix in some newer texts. This is achieved by encoding the graph as $(A
  * \cup C, E)$ where $A$ is the set of actors, `actorsIdentifiers`, and $C$ is the set of channels,
  * `channelsIdentifiers`. Every edge in $E$ connects an actor to a channel or a channel to an
  * actor, i.e. $e \in E$ means that $e \in A \times C$ or $e \in C \times A$. These edges are
  * encoded with `topologySrcs`, `topologyDsts` and `topologyEdgeValue` for the amount of tokens
  * produced or consumed. For example, if $e = (a, c, 2)$, then the edge $e$ is the production of 2
  * tokens from the actor $a$ to channel $c$. The other parameters bring enough instrumentation
  * information so that the decision model can potentially be mapped into a target platform.
  *
  * @param actorsIdentifiers
  *   the set of actors
  * @param channelsIdentifiers
  *   the set of channels
  * @param topologySrcs
  *   the sources for every edge triple in the SDF graph.
  * @param topologyDsts
  *   the target for every edge triple in the SDF graph.
  * @param topologyEdgeValue
  *   the produced or consumed tokens for each edge triple in the SDF graph.
  * @param actorSizes
  *   the size in bits for each actor's instruction(s)
  * @param actorThrouhgputs
  *   the fixed throughput expected to be done for each actor, given in executions per second.
  *
  * @see
  *   [[InstrumentedWorkloadMixin]] for descriptions of the computational and memory needs.
  */
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

  val dataflowGraphs = Vector(
    topologySrcs
      .zip(topologyDsts)
      .zipWithIndex
      .map((srcdst, i) => (srcdst._1, srcdst._2, topologyEdgeValue(i)))
  )

  def isSelfConcurrent(actor: String): Boolean = channelsIdentifiers.exists(c =>
    dataflowGraphs(0).exists((a, cc, _) =>
      cc == c && dataflowGraphs(0).exists((ccc, a, _) => ccc == c)
    )
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
  val repetitionVectors                 = computeRepetitionVectors
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
