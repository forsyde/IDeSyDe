package idesyde.identification.forsyde.models.sdf

import scala.jdk.CollectionConverters.*

import forsyde.io.java.core.Vertex
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import forsyde.io.java.core.ForSyDeSystemGraph
import org.jgrapht.Graph
import org.jgrapht.graph.WeightedPseudograph
import org.jgrapht.graph.AsWeightedGraph
import idesyde.utils.SDFUtils
import idesyde.identification.models.workload.ParametricRateDataflowWorkloadMixin
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import forsyde.io.java.typed.viewers.impl.Executable
import idesyde.identification.models.workload.InstrumentedWorkloadMixin
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import scala.collection.mutable
import forsyde.io.java.typed.viewers.impl.TokenizableDataBlock
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.AsSubgraph
import java.util.stream.Collectors
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import spire.math.*
import scala.collection.mutable.Buffer

import scalax.collection.GraphPredef._

final case class SDFApplication(
    val actors: Array[SDFActor],
    val channels: Array[SDFChannel],
    val topology: Graph[SDFActor | SDFChannel, DefaultEdge],
    actorFuncs: Array[Array[Executable]] = Array.empty
) extends ForSyDeDecisionModel
    with ParametricRateDataflowWorkloadMixin
    with InstrumentedWorkloadMixin {

  val actorFunctions =
    if (actorFuncs.isEmpty) then Array.fill(actors.size)(Array.empty[Executable]) else actorFuncs

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size
  val coveredVertexes =
    actors.map(_.getViewedVertex) ++
      channels.map(_.getViewedVertex)

  val actorsSet: Array[Int]   = (0 until actors.size).toArray
  val channelsSet: Array[Int] = (actors.size until (actors.size + channels.size)).toArray

  val initialTokens: Array[Int] = channels.map(_.getNumOfInitialTokens)

  def isSelfConcurrent(actorIdx: Int): Boolean = {
    val a = actors(actorIdx)
    !channels.exists(c => topology.containsEdge(a, c) && topology.containsEdge(c, a))
  }

  val dataflowGraphs = {
    val g = SimpleDirectedWeightedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    actors.zipWithIndex.foreach((a, i) => {
      channels.zipWithIndex.foreach((c, prej) => {
        val j = channelsSet(prej)
        topology.getAllEdges(a, c).forEach(p => g.addEdge(i, j, topology.getEdgeWeight(p).toInt))
        topology.getAllEdges(c, a).forEach(p => g.addEdge(j, i, topology.getEdgeWeight(p).toInt))
      })
    })
    Array(g.buildAsUnmodifiable())
  }

  val configurations = {
    val g = DefaultDirectedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    g.addEdge(0, 0)
    g.buildAsUnmodifiable
  }

  /** this is a simple shortcut for the balance matrix (originally called topology matrix) as SDFs
    * have only one configuration
    */
  val sdfBalanceMatrix: Array[Array[Int]] = computeBalanceMatrices(0)

  /** this is a simple shortcut for the repetition vectors as SDFs have only one configuration */
  val sdfRepetitionVectors: Array[Int] = computeRepetitionVectors(0)

  /** this is a simple shortcut for the max parallel clusters as SDFs have only one configuration */
  lazy val sdfMaxParallelClusters: Array[Array[Int]] = maximalParallelClustering(0)

  val processComputationalNeeds: Array[Map[String, Map[String, Long]]] =
    actorFunctions.zipWithIndex.map((actorFuncs, i) => {
      // we do it mutable for simplicity...
      // the performance hit should not be a concern now, for super big instances, this can be reviewed
      var mutMap = mutable.Map[String, mutable.Map[String, Long]]()
      actorFuncs.foreach(func => {
        InstrumentedExecutable
          .safeCast(func)
          .ifPresent(ifunc => {
            // now they have to be aggregated
            ifunc
              .getOperationRequirements()
              .entrySet()
              .forEach(e => {
                val innerMap = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Long])
                // first the intersection parts
                mutMap(e.getKey()) = mutMap
                  .getOrElse(e.getKey(), innerMap)
                  .map((k, v) => k -> (v + innerMap.getOrElse(k, 0L)))
                // now the parts only the other map has
                (innerMap.keySet -- mutMap(e.getKey()).keySet)
                  .map(k => mutMap(e.getKey())(k) = innerMap(k))
              })
          })
      })
      // check also the actor, just in case, this might be best
      // in case the functions don't exist, but the actors is instrumented
      // anyway
      InstrumentedExecutable
        .safeCast(actors(i))
        .ifPresent(ia => {
          // now they have to be aggregated
          ia
            .getOperationRequirements()
            .entrySet()
            .forEach(e => {
              val innerMap = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Long])
              // first the intersection parts
              mutMap(e.getKey()) = mutMap
                .getOrElse(e.getKey(), innerMap)
                .map((k, v) => k -> (v + innerMap.getOrElse(k, 0L)))
              // now the parts only the other map has
              (innerMap.keySet -- mutMap(e.getKey()).keySet)
                .map(k => mutMap(e.getKey())(k) = innerMap(k))
            })
        })
      mutMap.map((k, v) => k -> v.toMap).toMap
    })

  val processSizes: Array[Long] = actors.zipWithIndex.map((a, i) =>
    InstrumentedExecutable.safeCast(a).map(_.getSizeInBits().asInstanceOf[Long]).orElse(0L) +
      actorFunctions
        .flatMap(fs =>
          fs.map(
            InstrumentedExecutable.safeCast(_).map(_.getSizeInBits().asInstanceOf[Long]).orElse(0L)
          )
        )
        .sum
  )

  val messagesMaxSizes: Array[Long] = channels.zipWithIndex.map((c, i) =>
    pessimisticTokensPerChannel(i) * TokenizableDataBlock
      .safeCast(c)
      .map(d => d.getTokenSizeInBits())
      .orElse(0L)
  )

  val sdfDisjointComponents = disjointComponents.head

  val decreasingActorConsumptionOrder = actorsSet
    .sortBy(a => {
      sdfBalanceMatrix.zipWithIndex
        .filter((vec, c) => vec(a) < 0)
        .map((vec, c) =>
          -TokenizableDataBlock
            .safeCast(channels(c))
            .map(d => d.getTokenSizeInBits())
            .orElse(0L) * vec(a)
        )
        .sum
    })
    .reverse

  val messagesFromChannels = dataflowGraphs.zipWithIndex.map((df, dfi) => {
    var lumpedChannels = mutable.Map[(Int, Int), (Array[Int], Long, Int, Int, Int)]()
    for ((c, ci) <- channelsSet.zipWithIndex) {
      val thisInitialTokens = initialTokens(ci)
      df.incomingEdgesOf(c)
        .forEach(eSrc => {
          val src = df.getEdgeSource(eSrc)
          val sent = sdfBalanceMatrix(c - actors.size)(src) * TokenizableDataBlock
            .safeCast(channels(c - actors.size))
            .map(d => d.getTokenSizeInBits().toLong)
            .orElse(0L)
          val produced = sdfBalanceMatrix(c - actors.size)(src)
          df.outgoingEdgesOf(c)
            .forEach(eDst => {
              val dst      = df.getEdgeTarget(eDst)
              val consumed = -sdfBalanceMatrix(c - actors.size)(dst)
              if (lumpedChannels.contains((src, dst))) {
                val (cs, d, p, q, tok) = lumpedChannels((src, dst))
                lumpedChannels((src, dst)) = (
                  cs :+ c,
                  d + sent,
                  p + produced,
                  q + consumed,
                  tok + thisInitialTokens
                )
              } else {
                lumpedChannels((src, dst)) = (
                  Array(c),
                  sent,
                  produced,
                  consumed,
                  thisInitialTokens
                )
              }
            })
        })
    }
    lumpedChannels.map((k, v) => (k._1, k._2, v._1, v._2, v._3, v._4, v._5)).toArray
  })

  /** This abstracts the many sdf channels in the sdf multigraph into the form commonly presented in
    * papers and texts: with just a channel between every two actors.
    *
    * Every tuple in this is given by: (src actors index, dst actors index, lumped SDF channels,
    * size of message, produced, consumed, initial tokens)
    */
  val sdfMessages = messagesFromChannels(0)

  lazy val sdfGraph = {
    val edges = sdfMessages.map(t => (t._1, t._2))
    val param = edges.map((s, t) => (s ~> t))
    scalax.collection.Graph(param: _*)
  }

  /** This graph serves the same purpose as the common HSDF transformation, but simply stores
    * precedences between firings instead of data movement.
    */
  lazy val firingsPrecedenceGraph = {
    // val firings = sdfRepetitionVectors.zipWithIndex.map((a, q) => (1 to q).map(qa => (a, qa)))
    var edges = Buffer[((Int, Int), (Int, Int))]()
    for ((src, dst, _, _, produced, consumed, tokens) <- sdfMessages) {
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
            edges +:= ((src, qSrc), (dst, qDst))
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
    for (a <- actorsSet; q <- 1 to sdfRepetitionVectors(actorsSet.indexOf(a)) - 1) {
      edges +:= ((a, q), (a, q + 1))
    }
    val param = edges.map((s, t) => (s ~> t)).toArray
    scalax.collection.Graph(param: _*)
  }

  /** Same as [[firingsPrecedenceGraph]], but with one more firings per actors of the next periodic
    * phase
    */
  lazy val firingsPrecedenceWithExtraStepGraph = {
    var edges = Buffer[((Int, Int), (Int, Int))]()
    for ((src, dst, _, _, produced, consumed, tokens) <- sdfMessages) {
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
            edges +:= ((src, qSrc), (dst, qDst))
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
    for (a <- actorsSet; i = actorsSet.indexOf(a)) {
      edges +:= ((a, sdfRepetitionVectors(i)), (a, sdfRepetitionVectors(i) + 1))
    }
    val param = edges.map((s, t) => (s ~> t)).toArray
    firingsPrecedenceGraph ++ param
  }

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
    actorsSet.sortBy(a => topologicalAndHeavyJobOrdering.indexWhere((aa, _) => a == aa))

  lazy val topologicalAndHeavyActorOrderingWithExtra =
    actorsSet.sortBy(a => topologicalAndHeavyJobOrderingWithExtra.indexWhere((aa, _) => a == aa))

  override val uniqueIdentifier = "SDFApplication"

}
