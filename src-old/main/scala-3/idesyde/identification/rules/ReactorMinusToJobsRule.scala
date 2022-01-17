package idesyde.identification.rules

import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.LinguaFrancaTimer
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationRule
import idesyde.identification.models.reactor.ReactorMinusApplication
import idesyde.identification.models.reactor.ReactionJob
import idesyde.identification.models.reactor.ReactionChannel
import org.apache.commons.math3.fraction.BigFraction
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.traverse.BreadthFirstIterator

import collection.JavaConverters.*

@deprecated
final case class ReactorMinusToJobsRule() extends IdentificationRule {

  def identify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]) =
    val reactorMinusOpt = identified
      .find(_.isInstanceOf[ReactorMinusApplication])
      .map(_.asInstanceOf[ReactorMinusApplication])
    if (reactorMinusOpt.isDefined) {
      val reactorMinus              = reactorMinusOpt.get
      given ReactorMinusApplication = reactorMinus
      given Ordering[LinguaFrancaReaction] = reactorMinus.reactionsPriorityOrdering
      val periodicJobs              = computePeriodicJobs(model)
      val pureJobs                  = computePureJobs(model, periodicJobs)
      val jobs                      = periodicJobs ++ pureJobs
      val reactionsToJobs           = jobs.groupBy(_.srcReaction)
      given Map[LinguaFrancaReactor, Seq[ReactionJob]] = reactorMinus.reactors
        .map(a => a -> a.getReactionsPort(model).asScala.toSeq.flatMap(reactionsToJobs(_)))
        .toMap
      val stateChannels =
        computeTimelyChannels(model, jobs).union(computePriorityChannels(model, jobs))
      // val decisionModel = ReactorMinusJobs(
      //   reactorMinusApp = reactorMinus,
      //   periodicJobs = periodicJobs,
      //   pureJobs = pureJobs,
      //   pureChannels = computePureChannels(model, jobs),
      //   stateChannels = stateChannels,
      //   outerStateChannels = computeHyperperiodChannels(model, jobs, stateChannels)
      // )
      for (j <- jobs)
        if (j.deadline.compareTo(j.trigger) <= 0)
          scribe.error(s"${j.toString} has equal trigger and deadline!")
      // scribe.debug(s"channels ${decisionModel.channels.map(c => c.src.toString + "->" + c.dst.toString)}")
      // scribe.debug(
      //   s"Conforming ReactorMinusJobs found with Reactor-: " +
      //     s"${decisionModel.periodicJobs.size} per. Job(s), " +
      //     s"${decisionModel.pureJobs.size} pure Job(s) " +
      //     s"${decisionModel.pureChannels.size} pure channel(s), " +
      //     s"${decisionModel.stateChannels.size} inner state channel(s), " +
      //     s"${decisionModel.outerStateChannels.size} outer state channels, and "
      //   // s"${decisionModel.unambigousJobTriggerChains.size} job trigger chains"
      // )
      (true, Option.empty)
    } else if (ReactorMinusIdentificationRule.canIdentify(model, identified)) {
      (false, Option.empty)
    } else {
      scribe.debug("Cannot conform Reactor- jobs in the model.")
      (true, Option.empty)
    }

  def hasIdentifiedReactorMinus(identified: Set[DecisionModel]): Boolean =
    identified.exists(_.isInstanceOf[ReactorMinusApplication])

  def computePeriodicJobs(
      model: ForSyDeSystemGraph
  )(using reactorMinus: ReactorMinusApplication): Set[ReactionJob] =
    for (
      r <- reactorMinus.periodicReactions;
      period = reactorMinus.periodFunction.getOrElse(r, reactorMinus.hyperPeriod);
      i <- Seq.range(0, reactorMinus.hyperPeriod.divide(period).getNumerator.intValue)
    ) yield ReactionJob(r, period.multiply(i), period.multiply(i + 1))

  def computePureJobs(
      model: ForSyDeSystemGraph,
      periodicJobs: Set[ReactionJob]
  )(using reactorMinus: ReactorMinusApplication): Set[ReactionJob] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    // val paths = AllDirectedPaths(reactorMinus)
    val periodicReactionToJobs = periodicJobs.groupBy(_.srcReaction)
    val overlappedPureJobs = reactorMinus.periodicReactions.flatMap(r => {
      val iterator                     = BreadthFirstIterator(reactorMinus, r)
      val periodicJobs                 = periodicReactionToJobs(r)
      var pureJobSet: Set[ReactionJob] = Set()
      while iterator.hasNext do
        val cur = iterator.next
        if (reactorMinus.pureReactions.contains(cur))
          pureJobSet = pureJobSet ++ periodicJobs.map(j => {
            ReactionJob(cur, j.trigger, j.deadline)
          })
      pureJobSet
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
    // nonOverlapDeadlineSaveLast + sortedOverlap.last
  }

  def computePureChannels(
      model: ForSyDeSystemGraph,
      jobs: Set[ReactionJob]
  )(using
      reactorMinus: ReactorMinusApplication
  ): Set[ReactionChannel] =
    val reactionToJobs = jobs.groupBy(_.srcReaction)
    (for (
      ((r, rr) -> c) <- reactorMinus.channels;
      if reactorMinus.pureReactions.contains(rr);
      j  <- reactionToJobs(r);
      jj <- reactionToJobs(rr);
      // if the triggering time is the same
      if j != jj && j.trigger.equals(jj.trigger)
    ) yield ReactionChannel(j, jj, c)).toSet

  def computePriorityChannels(
      model: ForSyDeSystemGraph,
      jobs: Set[ReactionJob]
  )(using
      reactorMinus: ReactorMinusApplication,
      reactorToJobs: Map[LinguaFrancaReactor, Seq[ReactionJob]],
      order: Ordering[LinguaFrancaReaction]
  ): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      (t, jset) <- reactorToJobs(a).groupBy(
        _.trigger
      );
      j :: jj :: _ <- jset
        .sortBy(_.srcReaction)
        .sliding(2);
      if j != jj
    ) yield ReactionChannel(j, jj, a)

  def computeTimelyChannels(
      model: ForSyDeSystemGraph,
      jobs: Set[ReactionJob]
  )(using
      reactorMinus: ReactorMinusApplication,
      reactorToJobs: Map[LinguaFrancaReactor, Seq[ReactionJob]],
      order: Ordering[LinguaFrancaReaction]
  ): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      js :: jjs :: _ <- reactorToJobs(
        a
      )
        .groupBy(_.trigger)
        .toSeq
        .sortBy((t, js) => t)
        .map((t, js) => js.sortBy(_.srcReaction))
        .sliding(2)
    ) yield ReactionChannel(js.last, jjs.head, a)

  def computeHyperperiodChannels(
      model: ForSyDeSystemGraph,
      jobs: Set[ReactionJob],
      stateChannels: Set[ReactionChannel]
  )(using
      reactorMinus: ReactorMinusApplication,
      reactorToJobs: Map[LinguaFrancaReactor, Seq[ReactionJob]],
      order: Ordering[LinguaFrancaReaction]
  ): Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = reactorToJobs(a) // jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
        .groupBy(_.trigger)
        .toSeq
        .sortBy((t, js) => t)
        .map((t, js) => js.sortBy(_.srcReaction));
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

}

@deprecated
object ReactorMinusToJobsRule:

  def canIdentify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]): Boolean =
    ReactorMinusIdentificationRule.canIdentify(model, identified)

end ReactorMinusToJobsRule
