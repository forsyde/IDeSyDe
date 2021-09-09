package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import idesyde.identification.models.reactor.ReactorMinusJobsMapAndSched
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.DecisionModel
import idesyde.identification.models.reactor.ReactionJob
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.ProfiledFunction
import forsyde.io.java.typed.viewers.ProfiledProcessingModule
import org.apache.commons.math3.fraction.BigFraction

import collection.JavaConverters.*
import idesyde.identification.models.reactor.ReactorMinusJobs
import idesyde.identification.models.SchedulableNetworkedDigHW

final case class ReactorMinusJobsDSEIdentRule()
    extends IdentificationRule[ReactorMinusJobsMapAndSched]:

  override def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[ReactorMinusJobsMapAndSched]) =
    val reactorJobsModelOpt = identified.find(_.isInstanceOf[ReactorMinusJobs]).map(_.asInstanceOf[ReactorMinusJobs])
    val schedulablePlatformOpt = identified.find(_.isInstanceOf[SchedulableNetworkedDigHW]).map(_.asInstanceOf[SchedulableNetworkedDigHW])
    if (reactorJobsModelOpt.isDefined && schedulablePlatformOpt.isDefined) {
      val reactorJobsModel = reactorJobsModelOpt.get
      val schedulablePlatform = schedulablePlatformOpt.get
      given Set[ReactionJob] = reactorJobsModel.jobs
      given Set[GenericProcessingModule] = schedulablePlatform.hardware.processingElems
      given BigFraction = reactorJobsModel.reactorMinusApp.hyperPeriod
      val decisionModel = ReactorMinusJobsMapAndSched(
        reactorMinusJobs = reactorJobsModel,
        platform = schedulablePlatform,
        wcetFunction = computeWCETFunction(model),
        utilityFunction = computeUtilityFunction(model)
      )
      scribe.debug(s"Identified conformin Reactor- DSE problem")
      (true, Option(decisionModel))
    } else if(ReactorMinusJobsDSEIdentRule.canIdentify(model, identified))
      (false, Option.empty)
    else
      (true, Option.empty)
  end identify

  def computeWCETFunction(model: ForSyDeModel)(using
      jobs: Set[ReactionJob],
      procElems: Set[GenericProcessingModule]
  ): Map[(ReactionJob, GenericProcessingModule), BigFraction] =
    val iter = for (
      j  <- jobs;
      pe <- procElems;
      (provName, provSet) <- ProfiledProcessingModule
        .safeCast(pe)
        .map(pe => pe.getProvisions.asScala.toMap)
        .orElse(Map.empty);
      (reqName, reqSet) <- {
        // scribe.debug(s"trying $pe with ${j.srcReaction.getImplementationPort(model).get}")
        j.srcReaction
        .getImplementationPort(model)
        .flatMap(ProfiledFunction.safeCast(_))
        .map(f => f.getRequirements.asScala.toMap)
        .orElse(Map.empty)};
      if provSet.keySet.equals(reqSet.keySet)
    )
      // TODO: find a numerically stabler way to compute this function
      yield 
        (j, pe) -> BigFraction(
        provSet.asScala.map(op => op._2 * reqSet.get(op._1)).sum[Long].intValue,
        pe.getNominalFrequencyInHertz.toInt
      )
    iter.toMap

  def computeUtilityFunction(model: ForSyDeModel)(using
      jobs: Set[ReactionJob],
      procElems: Set[GenericProcessingModule],
      hyperPeriod: BigFraction
  ): Map[(ReactionJob, GenericProcessingModule), BigFraction] =
    val wcetFunction = computeWCETFunction(model)
    val iter = for (
      j  <- jobs;
      pe <- procElems;
      wcet = wcetFunction.get((j, pe))
      if wcet.isDefined
    ) 
      yield (j, pe) ->
        wcet.get.divide(hyperPeriod)
    iter.toMap

end ReactorMinusJobsDSEIdentRule

object ReactorMinusJobsDSEIdentRule:

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]) =
    ReactorMinusToJobsRule.canIdentify(model, identified) &&
    SchedulableNetDigHWIdentRule.canIdentify(model, identified)

end ReactorMinusJobsDSEIdentRule