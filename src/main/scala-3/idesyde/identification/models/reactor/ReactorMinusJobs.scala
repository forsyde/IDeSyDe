package idesyde.identification.models.reactor

import idesyde.identification.DecisionModel
import idesyde.identification.models.reactor.ReactorMinusApplication
import idesyde.identification.models.reactor.ReactionChannel
import org.jgrapht.graph.SimpleDirectedGraph
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import org.jgrapht.alg.shortestpath.AllDirectedPaths

import collection.JavaConverters.*
import org.jgrapht.traverse.ClosestFirstIterator
import org.jgrapht.traverse.DepthFirstIterator
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.GraphPath
import java.util.stream.Collectors
import scala.collection.mutable.Buffer
import scala.annotation.tailrec
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector

final case class ReactorMinusJobs(
    val periodicJobs: Set[ReactionJob],
    val pureJobs: Set[ReactionJob],
    val pureChannels: Set[ReactionChannel],
    val stateChannels: Set[ReactionChannel],
    val outerStateChannels: Set[ReactionChannel],
    val reactorMinusApp: ReactorMinusApplication
) extends SimpleDirectedWeightedGraph[ReactionJob, ReactionChannel](classOf[ReactionChannel])
    with DecisionModel:

  for (j <- periodicJobs) addVertex(j)
  for (j <- pureJobs) addVertex(j)
  for (c <- pureChannels) c match {
    case ReactionChannel.CommReactionChannel(src, dst, s) => addEdge(src, dst, c)
    case _                                                => true
  }
  for (c <- stateChannels) c match {
    case ReactionChannel.StateReactionChannel(src, dst, r) => addEdge(src, dst, c)
    case _                                                 => true
  }
  for (c <- outerStateChannels) c match {
    case ReactionChannel.StateReactionChannel(src, dst, r) => addEdge(src, dst, c)
    case _                                                 => true
  }

  val jobs: Set[ReactionJob] = pureJobs.union(periodicJobs)

  val channels: Set[ReactionChannel] = pureChannels ++ stateChannels ++ outerStateChannels

  val reactionToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]] =
    reactorMinusApp.reactions.map(r => r -> jobs.filter(j => j.srcReaction == r).toSet).toMap

  def coveredVertexes = reactorMinusApp.coveredVertexes

  override def dominates(o: DecisionModel) =
    super.dominates(o) && (o match {
      case o: ReactorMinusApplication => reactorMinusApp == o
      case _                          => true
    })

  val unambigousJobTriggerChains: Set[Seq[ReactionJob]] =
    for (c <- channels)
      setEdgeWeight(
        c,
        reactorMinusApp.hyperPeriod.subtract(c.dst.trigger.subtract(c.src.trigger)).doubleValue
      )
    // scribe.debug(s"SSC ${GabowStrongConnectivityInspector(this).getCondensation.vertexSet.size}")
    val reactorTriggerChains = reactorMinusApp.unambigousTriggerChains
    // val allPathsCalculator = AllDirectedPaths(this)
    // val reactionToJobs = jobs.groupBy(_.srcReaction)
    val graphAlgorithm = CHManyToManyShortestPaths(this)
    reactorTriggerChains.flatMap(l => {
      val sources = reactionToJobs(l.head)
      val filteredSources = sources.filter(j =>
        incomingEdgesOf(j)
          .stream()
          .map(_.src)
          .noneMatch(jj => sources.contains(jj) && jj.trigger.compareTo(j.trigger) < 0)
      )
      val sinks = reactionToJobs(l.last)
      val filteredSinks = sinks.filter(j =>
        outgoingEdgesOf(j)
          .stream()
          .map(_.dst)
          .noneMatch(jj => sinks.contains(jj) && jj.deadline.compareTo(j.deadline) > 0)
      )
      val allPaths = graphAlgorithm.getManyToManyPaths(filteredSources.asJava, filteredSinks.asJava)
      for (
        src <- filteredSources;
        dst <- filteredSinks;
        p = Option(allPaths.getPath(src, dst));
        if p.isDefined;
        if l.forall(r => p.get.getVertexList.stream().anyMatch(v => v.srcReaction == r))
      ) yield p.get.getVertexList.asScala.toSeq
    })
//   val subJobs = reactionToJobs(l.head)
//   scribe.debug(s"starting at ${l.head.getIdentifier}")
//   var jobPaths = subJobsToPaths(subJobs)
//   scribe.debug(s"starting with ${jobPaths.size}")
//   l.drop(1).foreach(r => {
//     scribe.debug(s"for ${r.getIdentifier}, after some... ${jobPaths.size}")
//     val curSubJobs = reactionToJobs(r)
//     val curSubPaths = subJobsToPaths(curSubJobs)
//     jobPaths = for (
//       prevPath <- jobPaths;
//       nextPath <- curSubPaths;
//       if containsEdge(prevPath.last, nextPath.head)
//     ) yield prevPath ++ nextPath
//   })
//   // scribe.debug(s"finding job trigger paths from ${srcReaction.getIdentifier}: ${sources.size} to ${dstReaction.getIdentifier}: ${sinks.size}")
//   // for (
//   //   path <- allPaths.getAllPaths(sources.asJava, sinks.asJava, true, null).asScala
//   //   if l.forall(v => path.getVertexList.stream.anyMatch(j => j.srcReaction == v))
//   // ) yield path.getVertexList.asScala.toSeq
//   jobPaths.map(_.toSeq).toSet
// })

// @tailrec
// def getPathsFromJob(job: ReactionJob, reactionChain: Seq[LinguaFrancaReaction], explored: Set[ReactionJob] = Set.empty): Set[Seq[ReactionJob]] =
//   reactionChain match {
//     case rnext :: rs =>
//       val neighs = outgoingEdgesOf(job).asScala.map(c => c.dst).diff(explored)
//       val nextJobs = neighs.filter(_.srcReaction == rnext)
//       val sameJobs = neighs.filter(_.srcReaction == job.srcReaction)
//       val nextPaths
//     case _ => Set(Seq(job))
//   }
// val subGraph = AsSubgraph(this, subJobs.asJava)
// val allPathsCalculator = AllDirectedPaths(subGraph)
// val sources = subJobs.filter(j => subGraph.incomingEdgesOf(j).isEmpty)
// val sinks = subJobs.filter(j => subGraph.outgoingEdgesOf(j).isEmpty)
// scribe.debug(s"checking with ${sources.size} sources and ${sinks.size} sinks in ${subJobs.size} jobs")
// val paths = allPathsCalculator.getAllPaths(sources.asJava, sinks.asJava, true, null)
//   .stream().map(p => p.getVertexList.asScala)
//   .collect(Collectors.toList())
//   .asScala.toSet
// val intsect = sources.intersect(sinks).toBuffer
// scribe.debug(s"paths ${paths.size} and ${intsect.size} intersect")
// paths + intsect

  def getPathsFromReaction(
      reactionChain: Seq[LinguaFrancaReaction],
      explored: Set[ReactionJob] = Set.empty
  )(using
      reactionToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]]
  ): Set[Seq[ReactionJob]] =
    reactionChain match {
      case r :: rs =>
        val jobs = reactionToJobs(r)
        Set.empty
      case _ => Set.empty
    }

end ReactorMinusJobs
