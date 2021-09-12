package idesyde.identification.rules

import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.LinguaFrancaTimer
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationRule
import idesyde.identification.models.reactor.ReactorMinusJobs
import idesyde.identification.models.reactor.ReactorMinusApplication
import idesyde.identification.models.reactor.ReactionJob
import idesyde.identification.models.reactor.ReactionChannel
import org.apache.commons.math3.fraction.BigFraction
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.traverse.BreadthFirstIterator

import collection.JavaConverters.*

type ResourceType   = GenericProcessingModule | GenericDigitalStorage | GenericDigitalInterconnect

final case class ReactorMinusToJobsRule() extends IdentificationRule[ReactorMinusJobs] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) =
    val reactorMinusOpt = identified
            .find(_.isInstanceOf[ReactorMinusApplication])
            .map(_.asInstanceOf[ReactorMinusApplication])
    if (reactorMinusOpt.isDefined) {
      val reactorMinus = reactorMinusOpt.get
      given ReactorMinusApplication = reactorMinus
      val periodicJobs = computePeriodicJobs(model)
      val pureJobs = computePureJobs(model, periodicJobs)
      val jobs = periodicJobs ++ pureJobs
      val reactionsToJobs = jobs.groupBy(_.srcReaction)
      given Map[LinguaFrancaReaction, Set[ReactionJob]] = reactionsToJobs
      given Map[LinguaFrancaReactor, Seq[ReactionJob]] = reactorMinus.reactors.map(a => 
        a -> a.getReactionsPort(model).asScala.toSeq.flatMap(reactionsToJobs(_))
        ).toMap
      val stateChannels = computeTimelyChannels(model, jobs).union(computePriorityChannels(model, jobs))
      val decisionModel = ReactorMinusJobs(
              reactorMinusApp = reactorMinus,
              periodicJobs = periodicJobs,
              pureJobs = pureJobs,
              pureChannels = computePureChannels(model, jobs),
              stateChannels = stateChannels,
              outerStateChannels = computeHyperperiodChannels(model, jobs, stateChannels)
            )
      scribe.debug(s"Conforming ReactorMinusJobs found with Reactor-: " +
              s"${decisionModel.periodicJobs.size} per. Job(s), " +
              s"${decisionModel.pureJobs.size} pure Job(s) " +
              s"${decisionModel.pureChannels.size} pure channel(s), " +
              s"${decisionModel.stateChannels.size} inner state channel(s), " +
              s"${decisionModel.outerStateChannels.size} outer state channels, and" +
              s"${decisionModel.unambigousJobTriggerChains.size} job trigger chains")
      (true, Option(decisionModel))
      } else if (ReactorMinusIdentificationRule.canIdentify(model, identified)) {
      (false, Option.empty)
    } else {
      scribe.debug("Cannot conform Reactor- jobs in the model.")
      (true, Option.empty)
    }

  def hasIdentifiedReactorMinus(identified: Set[DecisionModel]): Boolean =
    identified.exists(_.isInstanceOf[ReactorMinusApplication])


  def computePeriodicJobs(
      model: ForSyDeModel
  )(using reactorMinus: ReactorMinusApplication): Set[ReactionJob] =
    for (
      r <- reactorMinus.periodicReactions;
      period = reactorMinus.periodFunction.getOrElse(r, reactorMinus.hyperPeriod);
      i <- Seq.range(0, reactorMinus.hyperPeriod.divide(period).getNumerator.intValue)
    ) yield ReactionJob(r, period.multiply(i), period.multiply(i + 1))

  def computePureJobs(
        model: ForSyDeModel, periodicJobs: Set[ReactionJob]
  )(using reactorMinus: ReactorMinusApplication): Set[ReactionJob] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    // val paths = AllDirectedPaths(reactorMinus)
    val periodicReactionToJobs = periodicJobs.groupBy(_.srcReaction)
    val overlappedPureJobs = reactorMinus.periodicReactions.flatMap(r => {
      val iterator = BreadthFirstIterator(reactorMinus, r)
      val periodicJobs = periodicReactionToJobs(r)
      var pureJobSet: Set[ReactionJob] = Set()
      while 
        iterator.hasNext
      do
        val cur = iterator.next
        if (reactorMinus.pureReactions.contains(cur))
          pureJobSet = pureJobSet ++ periodicJobs.map(j => ReactionJob(cur, j.trigger, j.deadline))
      pureJobSet
    })
    //   for (
    //     periodicReaction <- reactorMinus.periodicReactions;
    //     //r <- reactorMinus.pureReactions;
    //     //if paths.getAllPaths(j._1, r, true, null).isEmpty
    //     iterator = BreadthFirstIterator(reactorMinus, periodicReaction);
    //     r <- iterator.
    //     // r <- reactorMinus.pureReactions;
    //     // paths = AllDirectedPaths(reactorMinus).getAllPaths(j._1, r, true, null)
    //     // if !paths.isEmpty
    // ) yield {
    //   // scribe.debug(s"between ${j._1.getIdentifier} and ${r.getIdentifier}: ${paths.size} paths")
    //   ReactionJob(r, j.trigger, j.deadline)
    // }
    val sortedOverlap = overlappedPureJobs.toSeq.sortBy(_.trigger)
    val nonOverlapDeadlineSaveLast = (for (
      i <- 0 until (sortedOverlap.size - 1);
      job = sortedOverlap(i);
      nextJob = sortedOverlap(i+1)
    ) yield ReactionJob(job.srcReaction, job.trigger, if nextJob.trigger.compareTo(job.deadline) > 0 then job.deadline else nextJob.trigger)).toSet
    nonOverlapDeadlineSaveLast + sortedOverlap.last
    // .map(j => j -> 
    //     // filter for higher start and same reaction, then get the trigger time, if any.
    //     overlappedPureJobs.filter(jj => j.trigger.compareTo(jj.trigger) < 0).filter(jj => jj._1 == j._1)
    //     .minByOption(_._2).map(_._2).getOrElse(j._3)
    // ).toMap
    // return the pure jobs with the newly computed dadlines
  }

  def computePureChannels(
      model: ForSyDeModel, jobs: Set[ReactionJob]
        )(using reactorMinus: ReactorMinusApplication, reactionsToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]]): Set[ReactionChannel] =
    (for (
      ((r, rr) -> c) <- reactorMinus.channels;
      j <- reactionsToJobs(r);
      jj <- reactionsToJobs(rr);
      // if the triggering time is the same
      if j != jj && j._2.equals(jj._2) && reactorMinus.pureReactions.contains(rr)
      // if the dst job is a pure job
      // if reactorMinus.pureReactions.contains(jj._1);
      // if the original reaction channels exist
      //originalChannel = reactorMinus.channels.get((j._1, jj._1));
      // if !originalChannel.isEmpty;
    ) yield ReactionChannel.CommReactionChannel(j, jj, c)).toSet

  def computePriorityChannels(
      model: ForSyDeModel, jobs: Set[ReactionJob]
        )(using reactorMinus: ReactorMinusApplication, reactorToJobs: Map[LinguaFrancaReactor, Seq[ReactionJob]]): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = reactorToJobs(a); //jobs.filter(j => reactorMinus.containmentFunction(j._1) == a);
      j <- jset; jj <- jset;
      // different but same release time
      if j != jj && j._2.equals(jj._2)//;
      // higher priority
      // if reactorMinus.priorityRelation.contains((j._1, jj._1))
          ) yield ReactionChannel(j, jj, a)

  def computeTimelyChannels(
      model: ForSyDeModel, jobs: Set[ReactionJob]
  )(using reactorMinus: ReactorMinusApplication, reactorToJobs: Map[LinguaFrancaReactor, Seq[ReactionJob]]): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      js <- reactorToJobs(a) // jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
        // the ordering of j and jj is reversed due to priority being `gt`
        .sortWith((j, jj) => reactorMinus.priorityRelation(jj._1, j._1)) 
        .sortBy(_._2)
        .sliding(2)
      // js <- jset.sliding(2)
      // go through jobs to check if jj is the most immediate and most prioritize sucessor
      // if !jobs.exists(o => 
      //   // triggering time
      //   jj._2.compareTo(o._2) >= 0 && o._2.compareTo(j._2) > 0 &&
      //   // higher priority
      //   reactorMinus.priorityRelation.contains((o._1, jj._1))
      //   )
          ) yield ReactionChannel(js.head, js.last, a)

  def computeHyperperiodChannels(
        model: ForSyDeModel, jobs: Set[ReactionJob], stateChannels: Set[ReactionChannel]
  )(using reactorMinus: ReactorMinusApplication, reactorToJobs: Map[LinguaFrancaReactor, Seq[ReactionJob]]): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = reactorToJobs(a) // jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
        .sortWith((j, jj) => reactorMinus.priorityRelation(jj._1, j._1)) 
        .sortBy(_._2);
      j = jset.last; jj = jset.head
      // same reactor
      // reactor = reactorMinus.containmentFunction.get(j._1);
      // if reactor == reactorMinus.containmentFunction(jj._1);
      // go through jobs to check if there is not connection between j and jj
      // if !jobs.exists(o => 
      //   // triggering time
      //   stateChannels.contains((o, j, reactor.get)) || stateChannels.contains((jj, o, reactor.get))
      //   )
          ) yield ReactionChannel(j, jj, a)

}

object ReactorMinusToJobsRule:

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]): Boolean = ReactorMinusIdentificationRule.canIdentify(model, identified)

end ReactorMinusToJobsRule
