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
import org.apache.commons.math3.fraction.Fraction
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
      given Map[LinguaFrancaReaction, Set[ReactionJob]] = reactorMinus.reactions.map(r => r -> jobs.filter(_.srcReaction == r)).toMap
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
              s"${decisionModel.stateChannels.size} inner state channel(s) and " +
              s"${decisionModel.outerStateChannels.size} outer state channels")
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
      i <- Seq.range(0, reactorMinus.hyperPeriod.divide(period).getNumerator)
    ) yield ReactionJob(r, period.multiply(i), period.multiply(i + 1))

  def computePureJobs(
      model: ForSyDeModel, periodicJobs: Set[ReactionJob]
  )(using reactorMinus: ReactorMinusApplication): Set[ReactionJob] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    // val paths = AllDirectedPaths(reactorMinus)
    val overlappedPureJobs = for (
        j <- periodicJobs;
        //r <- reactorMinus.pureReactions;
        //if paths.getAllPaths(j._1, r, true, null).isEmpty
        iterator = BreadthFirstIterator(reactorMinus, j.srcReaction);
        r <- iterator.asScala.filter(reactorMinus.pureReactions.contains(_))
        // r <- reactorMinus.pureReactions;
        // paths = AllDirectedPaths(reactorMinus).getAllPaths(j._1, r, true, null)
        // if !paths.isEmpty
    ) yield {
      // scribe.debug(s"between ${j._1.getIdentifier} and ${r.getIdentifier}: ${paths.size} paths")
      ReactionJob(r, j.trigger, j.deadline)
    }
    val nonOverlapDeadline = overlappedPureJobs.map(j => j -> 
        // filter for higher start and same reaction, then get the trigger time, if any.
        overlappedPureJobs.filter(jj => j.trigger.compareTo(jj.trigger) < 0).filter(jj => jj._1 == j._1)
        .minByOption(_._2).map(_._2).getOrElse(j._3)
    ).toMap
    // return the pure jobs with the newly computed dadlines
    overlappedPureJobs.map(j => ReactionJob(j.srcReaction, j.trigger, nonOverlapDeadline.getOrElse(j, j.deadline)))
  }

  def computePureChannels(
      model: ForSyDeModel, jobs: Set[ReactionJob]
        )(using reactorMinus: ReactorMinusApplication, reactionsToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]]): Set[ReactionChannel] =
    (for (
      ((r, rr) -> c) <- reactorMinus.channels;
      j <- reactionsToJobs(r);
      jj <- reactionsToJobs(rr);
      // if the triggering time is the same
      if j._2.equals(jj._2) && reactorMinus.pureReactions.contains(rr)
      // if the dst job is a pure job
      // if reactorMinus.pureReactions.contains(jj._1);
      // if the original reaction channels exist
      //originalChannel = reactorMinus.channels.get((j._1, jj._1));
      // if !originalChannel.isEmpty;
    ) yield ReactionChannel.CommReactionChannel(j, jj, c)).toSet

  def computePriorityChannels(
      model: ForSyDeModel, jobs: Set[ReactionJob]
        )(using reactorMinus: ReactorMinusApplication): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = jobs.filter(j => reactorMinus.containmentFunction(j._1) == a);
      j <- jset; jj <- jset;
      // different but same release time
      if j._2.equals(jj._2)//;
      // higher priority
      // if reactorMinus.priorityRelation.contains((j._1, jj._1))
          ) yield ReactionChannel(j, jj, a)

  def computeTimelyChannels(
      model: ForSyDeModel, jobs: Set[ReactionJob]
        )(using reactorMinus: ReactorMinusApplication): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      js <- jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
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
          )(using reactorMinus: ReactorMinusApplication): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
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
