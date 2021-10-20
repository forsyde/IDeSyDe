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
import scala.collection.mutable.Map as MutableMap
import scala.annotation.tailrec
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.traverse.BreadthFirstIterator
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.ArithmeticUtils
import org.jgrapht.graph.AsWeightedGraph
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm
import org.jgrapht.util.VertexToIntegerMapping
import org.jgrapht.graph.DefaultEdge
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import org.jgrapht.sux4j.SuccinctDirectedGraph
import org.jgrapht.alg.util.Pair
import scala.collection.mutable
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph
import org.jgrapht.opt.graph.sparse.IncomingEdgesSupport
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import org.jgrapht.alg.shortestpath.IntVertexDijkstraShortestPath

final case class ReactorMinusAppJobGraph(
    reactorMinus: ReactorMinusApplication,
    executer: ThreadPoolExecutor
) extends SimpleDirectedGraph[ReactionJob, ReactionChannel](classOf[ReactionChannel]):

  given Ordering[LinguaFrancaReaction] = reactorMinus.reactionsPriorityOrdering

  val jobPrecedenceOrdering: Ordering[ReactionJob] = new Ordering[ReactionJob] {

    def compare(j: ReactionJob, jj: ReactionJob): Int =
      j.trigger.compareTo(jj.trigger) match
        case 0 =>
          reactorMinus.reactionsPriorityOrdering.compare(j.srcReaction, jj.srcReaction)
        case a => a
  }

  val jobPriorityOrdering: Ordering[ReactionJob] = new Ordering[ReactionJob] {

    def compare(j: ReactionJob, jj: ReactionJob): Int =
      reactorMinus.reactionsPriorityOrdering.compare(j.srcReaction, jj.srcReaction)
  }
  given Ordering[ReactionJob] = jobPrecedenceOrdering

  val periodicJobs: Set[ReactionJob] = for (
    r <- reactorMinus.periodicReactions;
    period = reactorMinus.periodFunction.getOrElse(r, reactorMinus.hyperPeriod);
    i <- 0 until reactorMinus.hyperPeriod.divide(period).getNumerator.intValue
  ) yield ReactionJob(r, period.multiply(i), period.multiply(i + 1))

  val pureJobs: Set[ReactionJob] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    val periodicReactionToJobs = periodicJobs.groupBy(_.srcReaction)
    var overlappedPureJobs     = mutable.Set[ReactionJob]()
    reactorMinus.periodicReactions.foreach(r => {
      val iterator     = BreadthFirstIterator(reactorMinus, r)
      var delays       = MutableMap.from(reactorMinus.reactions.map(r => r -> BigFraction.ZERO))
      val periodicJobs = periodicReactionToJobs(r)

      while iterator.hasNext do
        val cur = iterator.next
        if (reactorMinus.pureReactions.contains(cur))
          // go up the tree and calculate the accumulated delay
          val prev = iterator.getParent(cur)
          val link = iterator.getSpanningTreeEdge(cur)
          if prev != null && link != null then
            delays(cur) = delays(prev).add(
              BigFraction.ONE
                .multiply(link.getPropagationDelayInSecsNumerator)
                .divide(link.getPropagationDelayInSecsDenominator)
            )
          else
            delays(cur) = delays(prev)
          overlappedPureJobs = overlappedPureJobs ++ periodicJobs.map(j => {
            ReactionJob(
              cur,
              j.trigger.add(delays(cur)),
              j.deadline.add(delays(cur))
            )
          })
    })
    val sortedOverlap = overlappedPureJobs
      .groupBy(j => (j.srcReaction, j.trigger))
      .map((_, js) => js.minBy(_.deadline))
    // sortedOverlap.toSet
    sortedOverlap
      .groupBy(_.srcReaction)
      .flatMap((r, js) => {
        val jsSorted = js.toSeq.sortBy(_.trigger)
        val nonOverlap =
          for (
            i <- 0 until (jsSorted.size - 1);
            job     = jsSorted(i);
            nextJob = jsSorted(i + 1)
          )
            yield ReactionJob(
              job.srcReaction,
              job.trigger,
              if job.deadline.compareTo(nextJob.trigger) <= 0 then job.deadline else nextJob.trigger
            )
        nonOverlap.appended(jsSorted.last)
      })
      .toSet
  }

  val jobs: Set[ReactionJob] = pureJobs.union(periodicJobs)

  for (j <- periodicJobs)
    if j.trigger.equals(j.deadline) then
      scribe.error(s"Job ${j.toString} has trigger == deadline! Behavior may be undefined")

  lazy val jobsOrdered = jobs.toArray.sorted

  val reactionToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]] =
    jobs.groupBy(j => j.srcReaction)

  val reactorToJobs: Map[LinguaFrancaReactor, Set[ReactionJob]] =
    jobs.groupBy(j => reactorMinus.containmentFunction(j.srcReaction))
  // reactorMinus.reactors
  // .map(a => a -> a.getReactionsPort(model).asScala.toSeq.flatMap(reactionsToJobs(_)))
  // .toMap

  val pureChannels: Set[ReactionChannel] =
    (for (
      ((r, rr) -> c) <- reactorMinus.channels;
      if reactorMinus.pureReactions.contains(rr);
      j  <- reactionToJobs(r);
      jj <- reactionToJobs(rr);
      // if the triggering time is the same
      if j != jj && j.trigger
        .add(
          BigFraction.ONE
            .multiply(c.getPropagationDelayInSecsNumerator)
            .divide(c.getPropagationDelayInSecsDenominator)
        )
        .equals(jj.trigger)
    ) yield ReactionChannel(j, jj, c)).toSet

  val priorityChannels: Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      (t, jset) <- reactorToJobs(a).groupBy(
        _.trigger
      );
      if jset.size > 1;
      j :: jj :: _ <- jset.toSeq
        .sortBy(_.srcReaction)
        .sliding(2);
      if j != jj
    ) yield ReactionChannel(j, jj, a)

  val timelyChannels: Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      js :: jjs :: _ <- reactorToJobs(
        a
      )
        .groupBy(_.trigger)
        .toSeq
        .sortBy((t, js) => t)
        .map((t, js) => js.toSeq.sortBy(_.srcReaction))
        .sliding(2)
    ) yield ReactionChannel(js.last, jjs.head, a)

  val stateChannels: Set[ReactionChannel] = priorityChannels ++ timelyChannels

  val outerStateChannels: Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = reactorToJobs(a) // jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
        .groupBy(_.trigger)
        .toSeq
        .sortBy((t, js) => t)
        .map((t, js) => js.toSeq.sortBy(_.srcReaction));
      js = jset.head; jjs = jset.last
      // same reactor
      // reactor = reactorMinus.containmentFunction.get(j._1);
      // if reactor == reactorMinus.containmentFunction(jj._1);
      // go through jobs to check if there is not connection between j and jj
      // if !jobs.exists(o =>
      //   // triggering time
      //   stateChannels.contains((o, j, reactor.get)) || stateChannels.contains((jj, o, reactor.get))
      //   )
    ) yield ReactionChannel(jjs.last, js.head, a)

  val inChannels: Set[ReactionChannel] = pureChannels ++ stateChannels

  val channels: Set[ReactionChannel] = inChannels ++ outerStateChannels

  lazy val channelsOrdered = channels.toArray

  for (j <- periodicJobs) addVertex(j)
  for (j <- pureJobs) addVertex(j)
  for (c <- pureChannels) addEdge(c.src, c.dst, c)
  for (c <- stateChannels) addEdge(c.src, c.dst, c)
  for (c <- outerStateChannels) addEdge(c.src, c.dst, c)

  private lazy val fasterSparseRepresentation =
    SparseIntDirectedGraph(
      jobsOrdered.size.asInstanceOf[java.lang.Integer],
      channelsOrdered
        .map(e =>
          Pair.of(
            jobsOrdered.indexOf(e.src).asInstanceOf[java.lang.Integer],
            jobsOrdered.indexOf(e.dst).asInstanceOf[java.lang.Integer]
          )
        ).toList.asJava,
      IncomingEdgesSupport.LAZY_INCOMING_EDGES
    )

  private lazy val weightedFasterRep =
    AsWeightedGraph(
      fasterSparseRepresentation,
      channelsOrdered.zipWithIndex.map((c, i) =>
        i.asInstanceOf[java.lang.Integer] -> {
          if (c.dst.trigger.compareTo(c.src.trigger) >= 0) then
            reactorMinus.hyperPeriod
              .subtract(c.dst.trigger.subtract(c.src.trigger))
              .doubleValue.asInstanceOf[java.lang.Double]
          else
            c.dst.trigger
              .add(reactorMinus.hyperPeriod)
              .subtract(c.src.trigger)
              .doubleValue.asInstanceOf[java.lang.Double]
        }
      ).toMap.asJava
    )  

  def checkJobPathIsReactionPath[LR <: Seq[LinguaFrancaReaction], LJ <: Seq[ReactionJob]](
      reactions: LR,
      jobs: LJ
  ): Boolean =
    val reactionIter = reactions.iterator
    val jobIter      = jobs.iterator
    while (reactionIter.hasNext && jobIter.hasNext) {
      val r = reactionIter.next
      var j = jobIter.next
      while (j.srcReaction == r && jobIter.hasNext) {
        j = jobIter.next
      }
    }
    !reactionIter.hasNext && !jobIter.hasNext

  def checkListsAreRelated[LR <: Seq[Integer], LJ <: java.util.List[Integer]](
      smaller: LR,
      bigger: LJ
  ): Boolean =
    val smallerIter = smaller.iterator
    val biggerIter      = bigger.iterator
    while (smallerIter.hasNext && biggerIter.hasNext) {
      val r = smallerIter.next
      var j = biggerIter.next
      while (j == r && biggerIter.hasNext) {
        j = biggerIter.next
      }
    }
    !smallerIter.hasNext && !biggerIter.hasNext
    

  lazy val jobLevelFixedLatencies: Map[(ReactionJob, ReactionJob), BigFraction] =
    // scribe.debug(s"SSC ${GabowStrongConnectivityInspector(this).getCondensation.vertexSet.size}")
    // var latenciesArray: Array[Array[BigFraction]] = Array.fill[BigFraction](jobsOrdered.size, jobsOrdered.size)(BigFraction.ZERO)
    // for (
    //   ci <- 0 until channelsOrdered.size;
    //   src = fasterSparseRepresentation.getEdgeSource(ci);
    //   dst = fasterSparseRepresentation.getEdgeTarget(ci);
    //   jobSrc = jobsOrdered(src);
    //   jobDst = jobsOrdered(dst)
    // ) do 
    //   latenciesArray(src)(dst) = 
    //     if (jobDst.trigger.compareTo(jobSrc.trigger) >= 0) then
    //       jobDst.trigger.subtract(jobSrc.trigger)
    //     else
    //       reactorMinus.hyperPeriod.subtract(jobDst.trigger.subtract(jobSrc.trigger))
    // for (
    //   i <- 0 until jobsOrdered.size;
    //   ii <- 1 until jobsOrdered.size;
    //   ci <- 0 until channelsOrdered.size;
    //   src = fasterSparseRepresentation.getEdgeSource(ci);
    //   dst = fasterSparseRepresentation.getEdgeTarget(ci);
    //   possible = latenciesArray(ii)(src).add(latenciesArray(src)(dst))
    // ) do
    //   if (possible.compareTo(latenciesArray(ii)(dst)) > 0) then
    //     latenciesArray(ii)(dst) = possible
    // jobsOrdered.zipWithIndex.flatMap((j, i) => 
    //   jobsOrdered.zipWithIndex
    //   .filter((jj, ii) => 
    //     latenciesArray(i)(ii).compareTo(BigFraction.ZERO) > 0
    //   )
    //   .map((jj, ii) =>
    //     (j, jj) -> latenciesArray(i)(ii)  
    //   )  
    // ).toMap
    // latenciesArray.flatMap(jArr => latenciesArray())
    val endToEndReactions = reactorMinus.unambigousEndToEndReactions
    // val allPathsCalculator = AllDirectedPaths(this)
    // val reactionToJobs = jobs.groupBy(_.srcReaction)
    val longestPathsBetweenJobs = DijkstraManyToManyShortestPaths(weightedFasterRep)
    endToEndReactions.map((srcdst, reactionPath) => {
        val (src, dst) = srcdst
        // val src = srcdst._1
        // val dst = srcdst._2
        val allSources = reactionToJobs(src)
        val sources = allSources.filter(j =>
          incomingEdgesOf(j)
            .stream()
            .map(_.src)
            .filter(jj => allSources.contains(jj))
            .noneMatch(jj => jj.trigger.compareTo(j.trigger) < 0)
        )
        val allSinks = reactionToJobs(dst)
        val sinks = allSinks.filter(j =>
          outgoingEdgesOf(j)
            .stream()
            .map(_.dst)
            .filter(jj => allSinks.contains(jj))
            .allMatch(jj => jj.deadline.compareTo(j.deadline) <= 0)
        )
        val ps = longestPathsBetweenJobs.getManyToManyPaths(
          jobsOrdered.zipWithIndex.filter((j, i) => sources.contains(j)).map((j, i) => i.asInstanceOf[Integer]).toSet.asJava,
          jobsOrdered.zipWithIndex.filter((j, i) => sinks.contains(j)).map((j, i) => i.asInstanceOf[Integer]).toSet.asJava
        )
        jobsOrdered.zipWithIndex
          .filter((j, i) => 
            sources.contains(j)
          )
          .flatMap((j, i) =>
            jobsOrdered.zipWithIndex
            .filter((jj, ii) =>
              sinks.contains(jj) &&
              ps.getWeight(i, ii) != Double.PositiveInfinity
            )
            .filter((jj, ii) =>
              checkJobPathIsReactionPath(
                reactionPath,
                ps.getPath(i, ii).getVertexList.asScala.map(pi => jobsOrdered(pi)).toSeq
              )
            )
            .map((jj, ii) =>
              (j, jj) -> jj.trigger.subtract(j.trigger)
            )
          )
        
        // for (
        //   srcIdx <- 0 until jobsOrdered.size;
        //   srcJob <- jobsOrdered(srcIdx);
        //   p = longestPathsBetweenJobs.getPaths(srcIdx)
        //   if p != null;
        //   dst <- 0 until jobsOrdered.size;
        //   dstJob <- jobsOrdered(dst);
        //   if checkJobPathIsReactionpathInt(reactionPath.toList, p.getVertexList.asScala.toList)
        // ) yield (srcJob, dstJob) -> p
      })
      .flatMap(a => a)
      .toMap
      // .map(jpaths => {
      //   jpaths.maxBy((_, p) => {
      //     p.getWeight
      //     // val lastJobOfPath = p.getVertexList.get(p.getLength - 1)
      //     // p.getWeight + lastJobOfPath.deadline.subtract(lastJobOfPath.trigger).doubleValue
      //   })
      // })
      // .map((srcdst, p) =>
      //   // redo the computation between ends, which is enough to get the static latency,
      //   (jobsOrdered(srcdst._1), jobsOrdered(srcdst._2))
      //     -> {
      //       val endJob   = jobsOrdered(p.getVertexList.get(p.getVertexList.size - 1))
      //       val firstJob = jobsOrdered(p.getVertexList.get(0))
      //       endJob.trigger.subtract(firstJob.trigger)
      //     }
      // )
      // .toMap

  val uniqueIdentifier = reactorMinus.uniqueIdentifier + "JobGraph"

end ReactorMinusAppJobGraph
