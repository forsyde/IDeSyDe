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

type ReactorJobType = (LinguaFrancaReaction, Fraction, Fraction)
type CommChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaSignal)
type StateChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaReactor)
type ResourceType   = GenericProcessingModule | GenericDigitalStorage | GenericDigitalInterconnect

final case class ReactorMinusToJobsRule() extends IdentificationRule[ReactorMinusJobs] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) =
    if (hasIdentifiedReactorMinus(identified)) {
      val reactorMinus = identified
        .find(_.isInstanceOf[ReactorMinusApplication])
        .get
        .asInstanceOf[ReactorMinusApplication]
      given ReactorMinusApplication = reactorMinus
      val periodicJobs = computePeriodicJobs(model)
      val pureJobs = computePureJobs(model, periodicJobs)
      val jobs = periodicJobs.union(pureJobs)
      val pureChannels = computePureChannels(model, jobs)
      val stateChannels = computeTimelyChannels(model, jobs).union(computePriorityChannels(model, jobs))
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
    coverable.subsetOf(covered)
  }

  def computePeriodicJobs(
      model: ForSyDeModel
  )(using reactorMinus: ReactorMinusApplication): Set[ReactorJobType] =
    for (
      r <- reactorMinus.periodicReactions; 
      period = reactorMinus.periodFunction.get(r).get;
      i <- Seq.range(0, reactorMinus.hyperPeriod().divide(period).getNumerator)
    ) yield (r, period.multiply(i), period.multiply(i + 1))

  def computePureJobs(
      model: ForSyDeModel, periodicJobs: Set[ReactorJobType]
  )(using reactorMinus: ReactorMinusApplication): Set[ReactorJobType] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    val overlappedPureJobs = for (
        j <- periodicJobs;
        r <- reactorMinus.pureReactions;
        paths = AllDirectedPaths(model).getAllPaths(j._1.getViewedVertex, r.getViewedVertex, true, null)
        if !paths.isEmpty
    ) yield (r, j._2, j._3)
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
      j <- jobs;
      jj <- jobs;
      // same reactor
      reactor = reactorMinus.containmentFunction.get(j._1);
      if reactor == reactorMinus.containmentFunction(jj._1);
      // same release time
      if j._2.equals(jj._2);
      // higher priority
      if reactorMinus.priorityRelation.contains((j._1, jj._1))
    ) yield (j, jj, reactor.get)

  def computeTimelyChannels(
      model: ForSyDeModel, jobs: Set[ReactorJobType]
  )(using reactorMinus: ReactorMinusApplication): Set[StateChannelType] =
    for (
      j <- jobs;
      jj <- jobs;
      // same reactor
      reactor = reactorMinus.containmentFunction.get(j._1);
      if reactor == reactorMinus.containmentFunction(jj._1);
      // go through jobs to check if jj is the most immediate and most prioritize sucessor
      if !jobs.exists(o => 
        // triggering time
        jj._2.compareTo(o._2) >= 0 && o._2.compareTo(j._2) > 0 &&
        // higher priority
        reactorMinus.priorityRelation.contains((o._1, jj._1))
        )
    ) yield (j, jj, reactor.get)

  def computeHyperperiodChannels(
      model: ForSyDeModel, jobs: Set[ReactorJobType], stateChannels: Set[StateChannelType]
  )(using reactorMinus: ReactorMinusApplication): Set[StateChannelType] =
    for (
      j <- jobs;
      jj <- jobs;
      // same reactor
      reactor = reactorMinus.containmentFunction.get(j._1);
      if reactor == reactorMinus.containmentFunction(jj._1);
      // go through jobs to check if there is not connection between j and jj
      if !jobs.exists(o => 
        // triggering time
        stateChannels.contains((o, j, reactor.get)) || stateChannels.contains((jj, o, reactor.get))
        )
    ) yield (jj, j, reactor.get)

}
