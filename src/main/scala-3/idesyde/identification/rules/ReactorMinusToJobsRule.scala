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
type JobChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaSignal)
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
      (true, Option.empty)
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


}
