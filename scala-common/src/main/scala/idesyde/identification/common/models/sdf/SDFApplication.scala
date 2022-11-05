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

final case class SDFApplication(
    val actors: Array[String],
    val channels: Array[String],
    val topologySrcs: Array[String],
    val topologyDsts: Array[String],
    val topologyEdgeValue: Array[Int],
    val actorSizes: Array[Long],
    val actorComputationalNeeds: Array[Map[String, Map[String, Long]]],
    val channelNumInitialTokens: Array[Int],
    val channelTokenSizes: Array[Long],
    val actorThrouhgputs: Array[Double]
) extends StandardDecisionModel
    with ParametricRateDataflowWorkloadMixin
    with InstrumentedWorkloadMixin {

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size
  val coveredElements         = (actors ++ channels).toSet
  val coveredElementRelations = topologySrcs.zip(topologyDsts).toSet

  val topology = Graph(
    topologySrcs
      .zip(topologyDsts)
      .zipWithIndex
      .map((srcdst, i) => srcdst._1 ~> srcdst._2 % topologyEdgeValue(i)): _*
  )

  /** this is a simple shortcut for the balance matrix (originally called topology matrix) as SDFs
    * have only one configuration
    */
  val sdfBalanceMatrix: Array[Array[Int]] = computeBalanceMatrices(0)

  /** this is a simple shortcut for the repetition vectors as SDFs have only one configuration */
  val sdfRepetitionVectors: Array[Int] = computeRepetitionVectors(0)

  /** this is a simple shortcut for the max parallel clusters as SDFs have only one configuration */
  // val sdfMaxParallelClusters: Array[Array[Int]] = maximalParallelClustering(0)

  def isSelfConcurrent(actor: String): Boolean = channels.exists(c =>
    topology.get(c).diSuccessors.exists(dst => dst.toOuter == actor) &&
      topology.get(c).diPredecessors.exists(src => src.toOuter == actor)
  )

  val dataflowGraphs = Array(
    topologySrcs
      .zip(topologyDsts)
      .zipWithIndex
      .map((srcdst, i) => (srcdst._1, srcdst._2, topologyEdgeValue(i)))
  )

  val configurations = Array((0, 0, "root"))

  val processComputationalNeeds = actorComputationalNeeds
  //   actorFunctions.zipWithIndex.map((actorFuncs, i) => {
  //     // we do it mutable for simplicity...
  //     // the performance hit should not be a concern now, for super big instances, this can be reviewed
  //     var mutMap = mutable.Map[String, mutable.Map[String, Long]]()
  //     actorFuncs.foreach(func => {
  //       InstrumentedExecutable
  //         .safeCast(func)
  //         .ifPresent(ifunc => {
  //           // now they have to be aggregated
  //           ifunc
  //             .getOperationRequirements()
  //             .entrySet()
  //             .forEach(e => {
  //               val innerMap = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Long])
  //               // first the intersection parts
  //               mutMap(e.getKey()) = mutMap
  //                 .getOrElse(e.getKey(), innerMap)
  //                 .map((k, v) => k -> (v + innerMap.getOrElse(k, 0L)))
  //               // now the parts only the other map has
  //               (innerMap.keySet -- mutMap(e.getKey()).keySet)
  //                 .map(k => mutMap(e.getKey())(k) = innerMap(k))
  //             })
  //         })
  //     })
  //     // check also the actor, just in case, this might be best
  //     // in case the functions don't exist, but the actors is instrumented
  //     // anyway
  //     InstrumentedExecutable
  //       .safeCast(actors(i))
  //       .ifPresent(ia => {
  //         // now they have to be aggregated
  //         ia
  //           .getOperationRequirements()
  //           .entrySet()
  //           .forEach(e => {
  //             val innerMap = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Long])
  //             // first the intersection parts
  //             mutMap(e.getKey()) = mutMap
  //               .getOrElse(e.getKey(), innerMap)
  //               .map((k, v) => k -> (v + innerMap.getOrElse(k, 0L)))
  //             // now the parts only the other map has
  //             (innerMap.keySet -- mutMap(e.getKey()).keySet)
  //               .map(k => mutMap(e.getKey())(k) = innerMap(k))
  //           })
  //       })
  //     mutMap.map((k, v) => k -> v.toMap).toMap
  //   })

  val processSizes = actorSizes
  //   InstrumentedExecutable.safeCast(a).map(_.getSizeInBits().asInstanceOf[Long]).orElse(0L) +
  //     actorFunctions
  //       .flatMap(fs =>
  //         fs.map(
  //           InstrumentedExecutable.safeCast(_).map(_.getSizeInBits().asInstanceOf[Long]).orElse(0L)
  //         )
  //       )
  //       .sum
  // )

  val messagesMaxSizes: Array[Long] =
    channels.zipWithIndex.map((c, i) => pessimisticTokensPerChannel(i) * channelTokenSizes(i))

  val sdfDisjointComponents = disjointComponents.head

  override val uniqueIdentifier = "SDFApplication"

}
