package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel

import collection.JavaConverters.*
import idesyde.identification.models.platform.{NetworkedDigitalHardware, SchedulableNetworkedDigHW}

import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.platform.runtime.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.platform.runtime.TimeTriggeredScheduler
import forsyde.io.java.typed.viewers.platform.runtime.RoundRobinScheduler
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.PlatformElem
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler

final case class SchedulableNetDigHWIdentRule() extends IdentificationRule {

  override def identify(
      model: ForSyDeSystemGraph,
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
        fixedPrioSchedulers.toArray,
        timeTrigSchedulers.toArray,
        roundRobinSchedulers.toArray
      )
      val decisionModel = SchedulableNetworkedDigHW(
        hardware = hardwareDecisionModel,
        schedulersFromPEs = schedulersFromPEs,
        fixedPriorityPEs = computeFixedPriorityPEs(
          model,
          hardwareDecisionModel.processingElems,
          fixedPrioSchedulers.toArray
        ).toArray,
        timeTriggeredPEs =
          computeTimeTriggeredPEs(model, hardwareDecisionModel.processingElems, timeTrigSchedulers.toArray).toArray,
        roundRobinPEs =
          computeRoundRobinPEs(model, hardwareDecisionModel.processingElems, roundRobinSchedulers.toArray).toArray,
        staticCyclicPEs = Array.empty
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
      model: ForSyDeSystemGraph,
      processingElems: Array[GenericProcessingModule],
      fixedPrioritySchedulers: Array[FixedPriorityScheduler],
      timeTrigSchedulers: Array[TimeTriggeredScheduler],
      roundRobinSchedulers: Array[RoundRobinScheduler]
  ): Map[
    GenericProcessingModule,
    AbstractScheduler
  ] =
    val schedulers: Array[AbstractScheduler] =
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
      model: ForSyDeSystemGraph,
      processingElems: Array[GenericProcessingModule],
      fixedPriorityPEs: Array[FixedPriorityScheduler]
  ): Array[GenericProcessingModule] =
    processingElems.filter(pe =>
      fixedPriorityPEs.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeFixedPriorityPEs

  def computeTimeTriggeredPEs(
      model: ForSyDeSystemGraph,
      processingElems: Array[GenericProcessingModule],
      timeTrigSchedulers: Array[TimeTriggeredScheduler]
  ): Array[GenericProcessingModule] =
    processingElems.filter(pe =>
      timeTrigSchedulers.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeTimeTriggeredPEs

  def computeRoundRobinPEs(
      model: ForSyDeSystemGraph,
      processingElems: Array[GenericProcessingModule],
      roundRobinSchedulers: Array[RoundRobinScheduler]
  ): Array[GenericProcessingModule] =
    processingElems.filter(pe =>
      roundRobinSchedulers.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeRoundRobinPEs

//   def hasSchedulers(model: ForSyDeSystemGraph)

}

object SchedulableNetDigHWIdentRule:

  def canIdentify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]): Boolean =
    val platformVertexes = model.vertexSet.stream
      .filter(PlatformElem.conforms(_))
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
      model: ForSyDeSystemGraph,
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
