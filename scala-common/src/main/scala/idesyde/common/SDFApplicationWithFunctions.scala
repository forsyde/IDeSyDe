package idesyde.common

import scala.jdk.CollectionConverters.*

import upickle.default.*

import scala.collection.mutable
import java.util.stream.Collectors
import spire.math.*
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.edge.Implicits._
import scala.collection.mutable.Buffer
import idesyde.core.CompleteDecisionModel

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
  * @param minimumActorThroughputs
  *   the fixed throughput expected to be done for each actor, given in executions per second.
  *
  * @see
  *   [[InstrumentedWorkloadMixin]] for descriptions of the computational and memory needs.
  */
final case class SDFApplicationWithFunctions(
    val actorsIdentifiers: Vector[String],
    val channelsIdentifiers: Vector[String],
    val topologySrcs: Vector[String],
    val topologyDsts: Vector[String],
    val topologyEdgeValue: Vector[Int],
    val actorSizes: Vector[Long],
    val actorComputationalNeeds: Vector[Map[String, Map[String, Long]]],
    val channelNumInitialTokens: Vector[Int],
    val channelTokenSizes: Vector[Long],
    val minimumActorThroughputs: Vector[Double]
) extends StandardDecisionModel
    with ParametricRateDataflowWorkloadMixin
    with InstrumentedWorkloadMixin
    with CompleteDecisionModel
    derives ReadWriter {

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size
  val coveredElements = (actorsIdentifiers ++ channelsIdentifiers).toSet ++ (topologySrcs
    .zip(topologyDsts)
    .toSet)
    .map(_.toString)

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

  /** This abstracts the many sdf channels in the sdf multigraph into the form commonly presented in
    * papers and texts: with just a channel between every two actors.
    *
    * Every tuple in this is given by: (src actors index, dst actors index, lumped SDF channels,
    * size of message, produced, consumed, initial tokens)
    */
  val sdfMessages = computeMessagesFromChannels(0)

  /** this is a simple shortcut for the balance matrix (originally called topology matrix) as SDFs
    * have only one configuration
    */
  val sdfBalanceMatrix: Vector[Vector[Int]] = computeBalanceMatrices(0)

  /** this is a simple shortcut for the repetition vectors as SDFs have only one configuration */
  val repetitionVectors                 = computeRepetitionVectors
  val sdfRepetitionVectors: Vector[Int] = repetitionVectors(0)

  val sdfDisjointComponents = disjointComponents.head

  val sdfPessimisticTokensPerChannel = pessimisticTokensPerChannel(repetitionVectors)

  val sdfGraph = Graph.from(
    actorsIdentifiers,
    sdfMessages.map((s, t, _, _, _, _, _) => s ~> t)
  )

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
      for (
        qDst <- 1 to sdfRepetitionVectors(dst);
        ratio = Rational(qDst * consumed - tokens, produced);
        qSrc <- ratio.floor.toInt to ratio.ceil.toInt;
        if qSrc > 0
      ) {
        edges +:= ((s, qSrc), (d, qDst))
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
  lazy val firingsPrecedenceGraphWithCycles = {
    val maxFiringPossible = sdfRepetitionVectors.max + 1
    var edges             = Buffer[((String, Int), (String, Int))]()
    for ((s, d, _, _, produced, consumed, tokens) <- sdfMessages) {
      val src = actorsIdentifiers.indexOf(s)
      val dst = actorsIdentifiers.indexOf(d)
      // println((produced, consumed, tokens))
      // val src = vec.indexWhere(_ > 0)
      // val dst = vec.indexWhere(_ < 0)
      for (
        qDst <- 1 to maxFiringPossible * sdfRepetitionVectors(dst);
        qSrc <- 1 to maxFiringPossible * sdfRepetitionVectors(src);
        ratio = Rational(qDst * consumed - tokens, produced);
        if qSrc == ratio.ceil.toInt;
        qSrcMod = ((qSrc - 1) % sdfRepetitionVectors(src)) + 1;
        qDstMod = ((qDst - 1) % sdfRepetitionVectors(dst)) + 1
      ) {
        edges +:= ((s, qSrcMod), (d, qDstMod))
      }
    }
    for ((a, ai) <- actorsIdentifiers.zipWithIndex; q <- 1 to sdfRepetitionVectors(ai) - 1) {
      edges +:= ((a, q), (a, q + 1))
    }
    val param = edges.map((s, t) => (s ~> t))
    val nodes = edges.map((s, t) => s).toSet ++ edges.map((s, t) => t).toSet
    scalax.collection.Graph.from(nodes, param)
  }

  lazy val jobsAndActors = firingsPrecedenceGraph.nodes.map(_.value).toVector

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

  lazy val topologicalAndHeavyJobOrderingWithExtra = firingsPrecedenceGraphWithCycles
    .topologicalSort()
    .fold(
      cycleNode => {
        println("CYCLE NODES DETECTED")
        firingsPrecedenceGraph.nodes.map(_.value).toArray
      },
      topo =>
        topo
          .withLayerOrdering(
            firingsPrecedenceGraphWithCycles.NodeOrdering((v1, v2) =>
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

  def bodyAsText: String = write(this)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  override val uniqueIdentifier = "SDFApplicationWithFunctions"

}
