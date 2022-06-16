package idesyde.identification.models.sdf

import forsyde.io.java.core.Vertex
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import org.apache.commons.math3.linear.Array2DRowFieldMatrix
import org.apache.commons.math3.fraction.FractionField
import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import forsyde.io.java.core.ForSyDeSystemGraph
import org.jgrapht.Graph
import org.jgrapht.graph.WeightedPseudograph
import org.jgrapht.graph.AsWeightedGraph
import idesyde.utils.SDFUtils
import org.apache.commons.math3.fraction.BigFraction
import idesyde.identification.models.workload.ParametricRateDataflowWorkloadMixin
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge

final case class SDFApplication(
    val actors: Array[SDFActor],
    val channels: Array[SDFChannel],
    val topology: Graph[SDFActor | SDFChannel, Int]
)(using Integral[BigFraction]) extends ForSyDeDecisionModel with ParametricRateDataflowWorkloadMixin {

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size
  val coveredVertexes =
    actors.map(_.getViewedVertex) ++
      channels.map(_.getViewedVertex)

  def numActors: Int = actors.size
  def numChannels: Int = channels.size

  val initialTokens: Array[Int] = channels.map(_.getNumOfInitialTokens)

  lazy val dataflowGraphs = {
    val g = DefaultDirectedGraph.createBuilder[Int, Int](() => 0)
    actors.zipWithIndex.foreach((a, i) => {
      channels.zipWithIndex.foreach((c, prej) => {
        val j = prej + numActors
        topology.getAllEdges(a, c).forEach(p => g.addEdge(i, j, p))
        topology.getAllEdges(c, a).forEach(p => g.addEdge(j, i, p))
      })
    })
    Array(g.buildAsUnmodifiable)
  }

  val configurations = {
    val g = DefaultDirectedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    g.addEdge(0, 0)
    g.buildAsUnmodifiable
  } 

  // lazy val topologyMatrix = {
  //   var m = Array.fill(topology.edgeSet.size)(Array.fill(topology.vertexSet.size)(0))
  //   channels.zipWithIndex.foreach((c, i) => {
  //     actors.zipWithIndex.foreach((a, j) => {
  //       m(i)(j) = topology.getAllEdges(a, c).stream.mapToInt(i => i).sum - topology.getAllEdges(c, a).stream.mapToInt(i => i).sum
  //     })
  //   })
  //   m
  // }

  // lazy val passSchedule = SDFUtils.getPASS(topologyMatrix, initialTokens)

  // lazy val isConsistent = passSchedule.size == actors.size

  override val uniqueIdentifier = "SDFApplication"

}
