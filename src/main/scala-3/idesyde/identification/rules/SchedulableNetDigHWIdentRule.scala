package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import idesyde.identification.models.SchedulableNetworkedDigHW
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.DecisionModel

import collection.JavaConverters.*
import forsyde.io.java.typed.viewers.TimeTriggeredScheduler
import forsyde.io.java.typed.viewers.RoundRobinScheduler
import forsyde.io.java.typed.viewers.AbstractDigitalModule
import idesyde.identification.models.NetworkedDigitalHardware
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.PlatformAbstraction
import java.util.stream.Collectors

final case class SchedulableNetDigHWIdentRule() extends IdentificationRule {

  override def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    val hardwareDecisionModelOpt = identified
      .find(_.isInstanceOf[NetworkedDigitalHardware])
      .map(_.asInstanceOf[NetworkedDigitalHardware])
    if (
      hardwareDecisionModelOpt.isDefined && SchedulableNetDigHWIdentRule.canIdentify(
        model,
        identified
      )
    ) {
      val hardwareDecisionModel = hardwareDecisionModelOpt.get
      val fixedPrioSchedulers = model.vertexSet.stream
        .filter(FixedPriorityScheduler.conforms(_))
        .map(FixedPriorityScheduler.safeCast(_).get())
        .collect(Collectors.toSet)
        .asScala
        .toSet
      val timeTrigSchedulers = model.vertexSet.asScala
        .filter(TimeTriggeredScheduler.conforms(_))
        .map(TimeTriggeredScheduler.safeCast(_).get())
        .toSet
      val roundRobinSchedulers = model.vertexSet.asScala
        .filter(RoundRobinScheduler.conforms(_))
        .map(RoundRobinScheduler.safeCast(_).get())
        .toSet
      val schedulersFromPEs = computeSchedulersFromPEs(
        model,
        hardwareDecisionModel.processingElems,
        fixedPrioSchedulers,
        timeTrigSchedulers,
        roundRobinSchedulers
      )
      val decisionModel = SchedulableNetworkedDigHW(
        hardware = hardwareDecisionModel,
        schedulersFromPEs = schedulersFromPEs,
        fixedPriorityPEs = computeFixedPriorityPEs(
          model,
          hardwareDecisionModel.processingElems,
          fixedPrioSchedulers
        ),
        timeTriggeredPEs =
          computeTimeTriggeredPEs(model, hardwareDecisionModel.processingElems, timeTrigSchedulers),
        roundRobinPEs =
          computeRoundRobinPEs(model, hardwareDecisionModel.processingElems, roundRobinSchedulers)
      )
      scribe.debug(
        "found a conforming Schedulable Networked HW Model with: " +
          s"${fixedPrioSchedulers.size} FP schedulers, " +
          s"${timeTrigSchedulers.size} TT schedulers, " +
          s"${roundRobinSchedulers.size} RR schedulers"
      )
      (
        true,
        Option(decisionModel)
      )
      // scribe.debug("Not conforming Schedulable Networked HW Model: not all PEs have schedulers.")
      // (true, Option.empty)
    } else if (NetworkedDigitalHWIdentRule.canIdentify(model, identified)) {
      (false, Option.empty)
    } else {
      (true, Option.empty)
    }
  end identify

  def computeSchedulersFromPEs(
      model: ForSyDeModel,
      processingElems: Set[GenericProcessingModule],
      fixedPrioritySchedulers: Set[FixedPriorityScheduler],
      timeTrigSchedulers: Set[TimeTriggeredScheduler],
      roundRobinSchedulers: Set[RoundRobinScheduler]
  ): Map[
    GenericProcessingModule,
    FixedPriorityScheduler | TimeTriggeredScheduler | RoundRobinScheduler
  ] =
    val schedulers: Set[FixedPriorityScheduler | TimeTriggeredScheduler | RoundRobinScheduler] =
      fixedPrioritySchedulers ++ timeTrigSchedulers ++ roundRobinSchedulers
    processingElems
      .map(pe =>
        pe -> schedulers
          .find(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
          .get
      )
      .toMap
  end computeSchedulersFromPEs

  def computeFixedPriorityPEs(
      model: ForSyDeModel,
      processingElems: Set[GenericProcessingModule],
      fixedPriorityPEs: Set[FixedPriorityScheduler]
  ): Set[GenericProcessingModule] =
    processingElems.filter(pe =>
      fixedPriorityPEs.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeFixedPriorityPEs

  def computeTimeTriggeredPEs(
      model: ForSyDeModel,
      processingElems: Set[GenericProcessingModule],
      timeTrigSchedulers: Set[TimeTriggeredScheduler]
  ): Set[GenericProcessingModule] =
    processingElems.filter(pe =>
      timeTrigSchedulers.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeTimeTriggeredPEs

  def computeRoundRobinPEs(
      model: ForSyDeModel,
      processingElems: Set[GenericProcessingModule],
      roundRobinSchedulers: Set[RoundRobinScheduler]
  ): Set[GenericProcessingModule] =
    processingElems.filter(pe =>
      roundRobinSchedulers.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeRoundRobinPEs

//   def hasSchedulers(model: ForSyDeModel)

}

object SchedulableNetDigHWIdentRule:

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]): Boolean =
    val platformVertexes = model.vertexSet.stream
      .filter(PlatformAbstraction.conforms(_))
      .collect(Collectors.toSet)
      .asScala
      .toSet
    val fixedPrioSchedulers = platformVertexes
      .filter(FixedPriorityScheduler.conforms(_))
      .map(FixedPriorityScheduler.safeCast(_).get())
    val timeTrigSchedulers = platformVertexes
      .filter(TimeTriggeredScheduler.conforms(_))
      .map(TimeTriggeredScheduler.safeCast(_).get())
    val roundRobinSchedulers = platformVertexes
      .filter(RoundRobinScheduler.conforms(_))
      .map(RoundRobinScheduler.safeCast(_).get())
    val processingElems = platformVertexes
      .filter(GenericProcessingModule.conforms(_))
      .map(GenericProcessingModule.safeCast(_).get())
    // val vertexesLeft = platformVertexes
    //   .diff(identified.flatMap(_.coveredVertexes))
    NetworkedDigitalHWIdentRule.canIdentify(model, identified) &&
    everyPEIsSchedulable(
      model,
      processingElems,
      fixedPrioSchedulers,
      timeTrigSchedulers,
      roundRobinSchedulers
    )
  end canIdentify

  def everyPEIsSchedulable(
      model: ForSyDeModel,
      processingElems: Set[GenericProcessingModule],
      fixedPriorityPEs: Set[FixedPriorityScheduler],
      timeTrigSchedulers: Set[TimeTriggeredScheduler],
      roundRobinSchedulers: Set[RoundRobinScheduler]
  ): Boolean =
    processingElems.forall(v =>
      timeTrigSchedulers.exists(t => model.hasConnection(v, t) || model.hasConnection(t, v)) ||
        fixedPriorityPEs.exists(t => model.hasConnection(v, t) || model.hasConnection(t, v)) ||
        roundRobinSchedulers.exists(t => model.hasConnection(v, t) || model.hasConnection(t, v))
    )
  end everyPEIsSchedulable

end SchedulableNetDigHWIdentRule
