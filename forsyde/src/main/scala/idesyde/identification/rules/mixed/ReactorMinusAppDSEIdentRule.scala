package idesyde.identification.rules.mixed

import idesyde.identification.IdentificationRule
import idesyde.identification.ForSyDeIdentificationRule
import idesyde.identification.rules.reactor.ReactorMinusIdentificationRule
import idesyde.identification.rules.platform.SchedulableNetDigHWIdentRule
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSched
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.reactor.ReactionJob
import org.apache.commons.math3.fraction.BigFraction

import collection.JavaConverters.*
import idesyde.identification.models.reactor.ReactorMinusApplication
import idesyde.identification.models.platform.SchedulableNetworkedDigHW
import forsyde.io.java.typed.viewers.moc.linguafranca.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import idesyde.identification.IdentificationResult

final case class ReactorMinusAppDSEIdentRule()
    extends ForSyDeIdentificationRule[ReactorMinusAppMapAndSched]:

  override def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ) =
    val reactorMinusOpt =
      identified
        .find(_.isInstanceOf[ReactorMinusApplication])
        .map(_.asInstanceOf[ReactorMinusApplication])
    val schedulablePlatformOpt = identified
      .find(_.isInstanceOf[SchedulableNetworkedDigHW])
      .map(_.asInstanceOf[SchedulableNetworkedDigHW])
    if (reactorMinusOpt.isDefined && schedulablePlatformOpt.isDefined) {
      val reactorMinus        = reactorMinusOpt.get
      val schedulablePlatform = schedulablePlatformOpt.get
      val ForSyDeDecisionModel = ReactorMinusAppMapAndSched(
        reactorMinus = reactorMinus,
        platform = schedulablePlatform,
        wcetFunction = computeWCETFunction(
          model,
          reactorMinus.reactions,
          schedulablePlatform.hardware.processingElems
        ),
        utilityFunction = computeUtilityFunction(
          model,
          reactorMinus.reactions,
          schedulablePlatform.hardware.processingElems,
          reactorMinus.hyperPeriod
        )
      )
      scribe.debug(s"Identified conformin Reactor- DSE problem")
      new IdentificationResult(true, Option(ForSyDeDecisionModel))
    } else if (ReactorMinusAppDSEIdentRule.canIdentify(model, identified))
      new IdentificationResult(false, Option.empty)
    else
      new IdentificationResult(true, Option.empty)
  end identifyFromForSyDe

  def computeWCETFunction(
      model: ForSyDeSystemGraph,
      reactions: Array[LinguaFrancaReaction],
      procElems: Array[GenericProcessingModule]
  ): Map[(LinguaFrancaReaction, GenericProcessingModule), BigFraction] =
    val iter = for (
      r  <- reactions;
      pe <- procElems;
      (provisionName, provisionSet) <- InstrumentedProcessingModule
        .safeCast(pe)
        .map(pe => pe.getModalInstructionsPerCycle.asScala.toMap)
        .orElse(Map.empty);
      (requirementName, requirementSet) <- r
        .getImplementationPort(model)
        .flatMap(InstrumentedExecutable.safeCast(_))
        .map(f => f.getOperationRequirements.asScala.toMap)
        .orElse(Map.empty);
      if provisionSet.keySet.equals(requirementSet.keySet)
    )
      yield (r, pe) -> BigFraction(
        provisionSet.asScala.map(op => op._2 * requirementSet.get(op._1)).map(_.ceil.toLong).sum,
        pe.getOperatingFrequencyInHertz
      )
    iter.toMap

  def computeUtilityFunction(
      model: ForSyDeSystemGraph,
      reactions: Array[LinguaFrancaReaction],
      procElems: Array[GenericProcessingModule],
      hyperPeriod: BigFraction
  ): Map[(LinguaFrancaReaction, GenericProcessingModule), BigFraction] =
    val wcetFunction = computeWCETFunction(model, reactions, procElems)
    val iter = for (
      r  <- reactions;
      pe <- procElems;
      wcet = wcetFunction.get((r, pe))
      if wcet.isDefined
    )
      yield (r, pe) ->
        wcet.get.divide(hyperPeriod)
    iter.toMap

end ReactorMinusAppDSEIdentRule

object ReactorMinusAppDSEIdentRule:

  def canIdentify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]) =
    ReactorMinusIdentificationRule.canIdentify(model, identified) &&
      SchedulableNetDigHWIdentRule.canIdentify(model, identified)

end ReactorMinusAppDSEIdentRule
