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

@deprecated
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
  val coveredElements =
    actors.map(_.getViewedVertex) ++
      channels.map(_.getViewedVertex)

  val actorsSet: Array[Int]   = (0 until actors.size).toArray
  val channelsSet: Array[Int] = (actors.size until (actors.size + channels.size)).toArray

  val initialTokens: Array[Int] = channels.map(_.getNumOfInitialTokens)

  /** this is a simple shortcut for the balance matrix (originally called topology matrix) as SDFs
    * have only one configuration
    */
  def sdfBalanceMatrix: Array[Array[Int]] = balanceMatrices.head

  /** this is a simple shortcut for the repetition vectors as SDFs have only one configuration */
  def sdfRepetitionVectors: Array[Int] = repetitionVectors(0)

  /** this is a simple shortcut for the max parallel clusters as SDFs have only one configuration */
  def sdfMaxParallelClusters: Array[Array[Int]] = maximalParallelClustering(0)

  def isSelfConcurrent(actorIdx: Int): Boolean = {
    val a = actors(actorIdx)
    !channels.exists(c => topology.containsEdge(a, c) && topology.containsEdge(c, a))
  }

  lazy val dataflowGraphs = {
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

  override val uniqueIdentifier = "SDFApplication"

}
