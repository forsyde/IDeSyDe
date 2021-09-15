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
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths

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
  for (c <- pureChannels) addEdge(c.src, c.dst, c)
  for (c <- stateChannels) addEdge(c.src, c.dst, c)
  for (c <- outerStateChannels) addEdge(c.src, c.dst, c)
    
  val jobs: Set[ReactionJob] = pureJobs.union(periodicJobs)

  val channels: Set[ReactionChannel] = pureChannels ++ stateChannels ++ outerStateChannels

  val reactionToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]] =
    reactorMinusApp.reactions.map(r => r -> jobs.filter(j => j.srcReaction == r).toSet).toMap

  val coveredVertexes = reactorMinusApp.coveredVertexes

  override def dominates(o: DecisionModel) =
    super.dominates(o) && (o match {
      case o: ReactorMinusApplication => reactorMinusApp == o
      case _                          => true
    })

  lazy val unambigousJobTriggerChains: Set[Seq[ReactionJob]] =
    for (c <- channels)
      setEdgeWeight(
        c,
        reactorMinusApp.hyperPeriod.subtract(c.dst.trigger.subtract(c.src.trigger)).doubleValue
      )
    // redefine for outers
    for (c <- outerStateChannels)
      setEdgeWeight(
        c,
        c.dst.trigger.add(reactorMinusApp.hyperPeriod).subtract(c.src.trigger).doubleValue
      )
    // scribe.debug(s"SSC ${GabowStrongConnectivityInspector(this).getCondensation.vertexSet.size}")
    val reactorTriggerChains = reactorMinusApp.unambigousTriggerChains
    // val allPathsCalculator = AllDirectedPaths(this)
    // val reactionToJobs = jobs.groupBy(_.srcReaction)
    val graphAlgorithm = CHManyToManyShortestPaths(this)
    reactorTriggerChains.map(l => {
      val allSources = reactionToJobs(l.head)
      val sources = allSources.filter(j =>
        incomingEdgesOf(j)
          .stream()
          .map(_.src)
          .noneMatch(jj => allSources.contains(jj) && jj.trigger.compareTo(j.trigger) < 0)
      )
      val debugIterator = DepthFirstIterator(this, sources.asJava)
      while debugIterator.hasNext() do
        val n = debugIterator.next()
        val top = debugIterator.getStack.peek
        scribe.debug(s"top ${top.toString}, n ${n.toString}")
      val allSinks = reactionToJobs(l.last)
      val sinks = allSinks.filter(j =>
        outgoingEdgesOf(j)
          .stream()
          .map(_.dst)
          .noneMatch(jj => allSinks.contains(jj) && jj.deadline.compareTo(j.deadline) > 0)
      )
      // val allPaths = graphAlgorithm.getManyToManyPaths(sources.asJava, sinks.asJava)
      scribe.debug(s"from ${l.head.getIdentifier} to ${l.last.getIdentifier}")
      scribe.debug(s"sources ${sources.map(_.toString)}")
      scribe.debug(s"sinks ${sinks.map(_.toString)}")
      val s = for (
        src <- sources;
        dst <- sinks;
        p = Option(graphAlgorithm.getPath(src, dst))
        // {
        //   val ps = 
        //   scribe.debug(s"src ${src.toString} to ${dst.toString}: ${ps.map(_.getLength).getOrElse(-1).toString}")
        //   ps
        // };
        if p.isDefined;
        if l.forall(r => p.get.getVertexList.stream().anyMatch(v => v.srcReaction.equals(r)))
      ) yield {
        scribe.debug(s"${src.toString} to ${dst.toString}, ${p.get.getLength}")
        p.get
      }
      scribe.debug(s"resulting set ${s.size}")
      s
    }).map(jpaths => {
      scribe.debug(s"size ${jpaths.size}")
      jpaths.maxBy(p => {
        val lastJobOfPath = p.getVertexList.get(p.getLength - 1)
        p.getWeight + lastJobOfPath.deadline.subtract(lastJobOfPath.trigger).doubleValue
      })
    }).map(p => p.getVertexList.asScala.toSeq)
      



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

  override val uniqueIdentifier = "ReactorMinusJobs"

end ReactorMinusJobs
