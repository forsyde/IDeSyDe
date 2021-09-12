package idesyde.identification.models.reactor

import idesyde.identification.DecisionModel
import idesyde.identification.models.reactor.ReactorMinusApplication
import idesyde.identification.models.reactor.ReactionChannel
import org.jgrapht.graph.SimpleDirectedGraph
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import org.jgrapht.alg.shortestpath.AllDirectedPaths

import collection.JavaConverters.*

final case class ReactorMinusJobs(
    val periodicJobs: Set[ReactionJob],
    val pureJobs: Set[ReactionJob],
    val pureChannels: Set[ReactionChannel],
    val stateChannels: Set[ReactionChannel],
    val outerStateChannels: Set[ReactionChannel],
    val reactorMinusApp: ReactorMinusApplication
    ) extends SimpleDirectedGraph[ReactionJob, ReactionChannel](classOf[ReactionChannel])
    with DecisionModel:


  for (j <- periodicJobs) addVertex(j)
  for (j <- pureJobs) addVertex(j)
  for (c <- pureChannels) c match {
    case ReactionChannel.CommReactionChannel(src, dst, s) => addEdge(src, dst, c)
    case _ => true
  }
  for (c <- stateChannels) c match {
    case ReactionChannel.StateReactionChannel(src, dst, r) => addEdge(src, dst, c)
    case _ => true
  }
  for (c <- outerStateChannels) c match {
    case ReactionChannel.StateReactionChannel(src, dst, r) => addEdge(src, dst, c)
    case _ => true
  }

  val jobs: Set[ReactionJob] = pureJobs.union(periodicJobs)

  val reactionToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]] =
    reactorMinusApp.reactions.map(r =>
      r -> jobs.filter(j => j.srcReaction == r).toSet
      ).toMap

  def coveredVertexes = reactorMinusApp.coveredVertexes

  override def dominates(o: DecisionModel) =
    super.dominates(o) && (o match {
      case o: ReactorMinusApplication => reactorMinusApp == o
      case _                          => true
    })

  val unambigousJobTriggerChains: Set[Seq[ReactionJob]] =
    val reactorTriggerChains = reactorMinusApp.unambigousTriggerChains
    reactorTriggerChains.flatMap(l => {
      val srcReaction = l.head
      val dstReaction = l.last
      val sources = reactionToJobs(srcReaction)
      val sinks = reactionToJobs(dstReaction)
      val allPaths = AllDirectedPaths(this)
      scribe.debug(s"finding job trigger paths from ${srcReaction.getIdentifier}: ${sources.size} to ${dstReaction.getIdentifier}: ${sinks.size}")
      // for (
      //   path <- allPaths.getAllPaths(sources.asJava, sinks.asJava, true, null).asScala
      //   if l.forall(v => path.getVertexList.stream.anyMatch(j => j.srcReaction == v))
      // ) yield path.getVertexList.asScala.toSeq
      Set.empty
    })
    Set.empty

end ReactorMinusJobs
