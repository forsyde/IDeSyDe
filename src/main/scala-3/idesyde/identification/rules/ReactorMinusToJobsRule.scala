package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.ReactorMinusJobs
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import idesyde.identification.models.ReactorMinusApplication
import collection.JavaConverters.*
import forsyde.io.java.typed.viewers.LinguaFrancaTimer
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import org.apache.commons.math3.fraction.Fraction
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.traverse.BreadthFirstIterator

type ReactorJobType = (LinguaFrancaReaction, Fraction, Fraction)
type CommChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaSignal)
type StateChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaReactor)
type ResourceType   = GenericProcessingModule | GenericDigitalStorage | GenericDigitalInterconnect

final case class ReactorMinusToJobsRule() extends IdentificationRule[ReactorMinusJobs] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) =
    if (hasIdentifiedReactorMinus(identified)) {
      scribe.debug("Conforming Reactor- jobs model found.")
      val reactorMinus = identified
        .find(_.isInstanceOf[ReactorMinusApplication])
        .get
        .asInstanceOf[ReactorMinusApplication]
      given ReactorMinusApplication = reactorMinus
      scribe.debug(s"LCM is ${reactorMinus.hyperPeriod()}")
      val periodicJobs = computePeriodicJobs(model)
      scribe.debug(s"${periodicJobs.size} periodic jobs computed")
      val pureJobs = computePureJobs(model, periodicJobs)
      scribe.debug(s"${pureJobs.size} pure jobs computed")
      val jobs = periodicJobs.union(pureJobs)
      val pureChannels = computePureChannels(model, jobs)
      scribe.debug(s"${pureChannels.size} pure job channels computed")
      val stateChannels = computeTimelyChannels(model, jobs).union(computePriorityChannels(model, jobs))
      scribe.debug(s"${stateChannels.size} state job channels computed")
      val outerStateChannels = computeHyperperiodChannels(model, jobs, stateChannels)
      (true, Option(ReactorMinusJobs(
        reactorMinusApp = reactorMinus,
        periodicJobs = periodicJobs,
        pureJobs = pureJobs,
        pureChannels = pureChannels,
        stateChannels = stateChannels,
        outerStateChannels = outerStateChannels
      )))
    } else if (!canIdentify(model, identified)) {
      scribe.debug("Cannot conform Reactor- jobs in the model.")
      (true, Option.empty)
    } else {
      (false, Option.empty)
    }

  def hasIdentifiedReactorMinus(identified: Set[DecisionModel]): Boolean =
    identified.exists(_.isInstanceOf[ReactorMinusApplication])

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]): Boolean = {
    val coverable = model.vertexSet.asScala.filter(v =>
      LinguaFrancaReactor.conforms(v) ||
        LinguaFrancaTimer.conforms(v) ||
        LinguaFrancaReaction.conforms(v) ||
        LinguaFrancaSignal.conforms(v)
    )
    val covered = identified.flatMap(_.coveredVertexes())
    covered.subsetOf(coverable)
  }

  def computePeriodicJobs(
      model: ForSyDeModel
  )(using reactorMinus: ReactorMinusApplication): Set[ReactorJobType] =
    for (
      r <- reactorMinus.periodicReactions; 
      period = reactorMinus.periodFunction.getOrElse(r, reactorMinus.hyperPeriod());
      i <- Seq.range(0, reactorMinus.hyperPeriod().divide(period).getNumerator)
    ) yield (r, period.multiply(i), period.multiply(i + 1))

  def computePureJobs(
      model: ForSyDeModel, periodicJobs: Set[ReactorJobType]
  )(using reactorMinus: ReactorMinusApplication): Set[ReactorJobType] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    val overlappedPureJobs = for (
        j <- periodicJobs;
        iterator = BreadthFirstIterator(reactorMinus, j._1);
        r <- iterator.asScala.filter(reactorMinus.pureReactions.contains(_))
        // r <- reactorMinus.pureReactions;
        // paths = AllDirectedPaths(reactorMinus).getAllPaths(j._1, r, true, null)
        // if !paths.isEmpty
    ) yield {
      // scribe.debug(s"between ${j._1.getIdentifier} and ${r.getIdentifier}: ${paths.size} paths")
      (r, j._2, j._3)
    }
    val nonOverlapDeadline = overlappedPureJobs.map(j => j -> 
        // filter for higher start and same reaction, then get the trigger time, if any.
        overlappedPureJobs.filter(jj => j._2.compareTo(jj._2) < 0).filter(jj => jj._1 == j._1)
        .minByOption(_._2).map(_._2).getOrElse(j._3)
    ).toMap
    // return the pure jobs with the newly computed dadlines
    overlappedPureJobs.map(j => (j._1, j._2, nonOverlapDeadline.getOrElse(j, j._3)))
  }

  def computePureChannels(
      model: ForSyDeModel, jobs: Set[ReactorJobType]
  )(using reactorMinus: ReactorMinusApplication): Set[CommChannelType] =
    for (
      j <- jobs;
      jj <- jobs;
      // if the dst job is a pure job
      if reactorMinus.pureReactions.contains(jj._1);
      // if the original reaction channels exist
      originalChannel = reactorMinus.channels.get((j._1, jj._1));
      if !originalChannel.isEmpty;
      // if the triggering time is the same
      if j._2.equals(jj._2)
    ) yield (j, jj, originalChannel.get)

  def computePriorityChannels(
      model: ForSyDeModel, jobs: Set[ReactorJobType]
  )(using reactorMinus: ReactorMinusApplication): Set[StateChannelType] =
    for (
      a <- reactorMinus.reactors;
      jset = jobs.filter(j => reactorMinus.containmentFunction(j._1) == a);
      j <- jset; jj <- jset;
      // different but same release time
      if j._2.equals(jj._2);
      // higher priority
      if reactorMinus.priorityRelation.contains((j._1, jj._1))
    ) yield (j, jj, a)

  def computeTimelyChannels(
      model: ForSyDeModel, jobs: Set[ReactorJobType]
  )(using reactorMinus: ReactorMinusApplication): Set[StateChannelType] =
    for (
      a <- reactorMinus.reactors;
      jset = jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq.sortBy(_._2);
      // TODO: also consider the prioritized sucessor!
      js <- jset.sliding(2)
      // go through jobs to check if jj is the most immediate and most prioritize sucessor
      // if !jobs.exists(o => 
      //   // triggering time
      //   jj._2.compareTo(o._2) >= 0 && o._2.compareTo(j._2) > 0 &&
      //   // higher priority
      //   reactorMinus.priorityRelation.contains((o._1, jj._1))
      //   )
    ) yield (js.head, js.last, a)

  def computeHyperperiodChannels(
      model: ForSyDeModel, jobs: Set[ReactorJobType], stateChannels: Set[StateChannelType]
  )(using reactorMinus: ReactorMinusApplication): Set[StateChannelType] =
    for (
      a <- reactorMinus.reactors;
      jset = jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq.sortBy(_._2);
      j = jset.last; jj = jset.head
      // same reactor
      // reactor = reactorMinus.containmentFunction.get(j._1);
      // if reactor == reactorMinus.containmentFunction(jj._1);
      // go through jobs to check if there is not connection between j and jj
      // if !jobs.exists(o => 
      //   // triggering time
      //   stateChannels.contains((o, j, reactor.get)) || stateChannels.contains((jj, o, reactor.get))
      //   )
    ) yield (j, jj, a)

}
