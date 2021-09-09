package idesyde.identification.models.reactor

import idesyde.identification.DecisionModel
import idesyde.identification.models.reactor.ReactorMinusApplication
import org.jgrapht.graph.SimpleDirectedGraph

final case class ReactorMinusJobs(
    val periodicJobs: Set[ReactionJob],
    val pureJobs: Set[ReactionJob],
    val pureChannels: Set[ReactionChannel],
    val stateChannels: Set[ReactionChannel],
    val outerStateChannels: Set[ReactionChannel],
    val reactorMinusApp: ReactorMinusApplication
    ) extends SimpleDirectedGraph[ReactionJob, ReactionChannel](classOf[ReactionChannel])
    with DecisionModel {

  def jobs: Set[ReactionJob] = pureJobs.union(periodicJobs)

  def coveredVertexes = reactorMinusApp.coveredVertexes

  def coveredEdges = reactorMinusApp.coveredEdges

  override def dominates(o: DecisionModel) =
    super.dominates(o) && (o match {
      case o: ReactorMinusApplication => reactorMinusApp == o
      case _                          => true
    })
}
