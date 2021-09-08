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

final case class SchedulableNetDigHWIdentRule()
    extends IdentificationRule[SchedulableNetworkedDigHW] {

  override def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[SchedulableNetworkedDigHW]) =
    val hardwareDecisionModelOpt = identified
      .find(_.isInstanceOf[NetworkedDigitalHardware])
      .map(_.asInstanceOf[NetworkedDigitalHardware])
    if (!hardwareDecisionModelOpt.isEmpty) {
      val hardwareDecisionModel = hardwareDecisionModelOpt.get
      val timeTrigSchedulers = model.vertexSet.asScala
        .filter(TimeTriggeredScheduler.conforms(_))
        .map(TimeTriggeredScheduler.safeCast(_).get())
        .toSet
      val roundRobinSchedulers = model.vertexSet.asScala
        .filter(RoundRobinScheduler.conforms(_))
        .map(RoundRobinScheduler.safeCast(_).get())
        .toSet
      given Set[TimeTriggeredScheduler]  = timeTrigSchedulers
      given Set[RoundRobinScheduler]     = roundRobinSchedulers
      given Set[GenericProcessingModule] = hardwareDecisionModel.processingElems
      if (everyPEIsSchedulable(model)) {
        val schedulersFromPEs = computeSchedulersFromPEs(model)
        scribe.debug("found a conforming Schedulable Networked HW Model.")
        (
          true,
          Option(
            SchedulableNetworkedDigHW(
              hardware = hardwareDecisionModel,
              schedulersFromPEs = schedulersFromPEs,
              timeTriggeredPEs = computeTimeTriggeredPEs(model),
              roundRobinPEs = computeRoundRobinPEs(model)
            )
          )
        )
      } else 
        scribe.debug("Not conforming Schedulable Networked HW Model: not all PEs have schedulers.")
        (true, Option.empty)
    } else if (canIdentify(model, identified)) {
      (false, Option.empty)
    } else {
      (true, Option.empty)
    }
  end identify

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]): Boolean =
    val vertexesLeft = model.vertexSet.asScala
      .diff(identified.flatMap(_.coveredVertexes))
    vertexesLeft.exists(v =>
      TimeTriggeredScheduler.conforms(v) ||
        RoundRobinScheduler.conforms(v)
    ) &&
    vertexesLeft.exists(v => GenericProcessingModule.conforms(v))
  end canIdentify

  def everyPEIsSchedulable(model: ForSyDeModel)(using
      processingElems: Set[GenericProcessingModule],
      timeTrigSchedulers: Set[TimeTriggeredScheduler],
      roundRobinSchedulers: Set[RoundRobinScheduler]
  ): Boolean =
    processingElems.forall(v =>
      timeTrigSchedulers.exists(t => model.hasConnection(v, t) || model.hasConnection(t, v)) ||
        roundRobinSchedulers.exists(t => model.hasConnection(v, t) || model.hasConnection(t, v))
    )
  end everyPEIsSchedulable

  def computeSchedulersFromPEs(
      model: ForSyDeModel
  )(using
      processingElems: Set[GenericProcessingModule],
      timeTrigSchedulers: Set[TimeTriggeredScheduler],
      roundRobinSchedulers: Set[RoundRobinScheduler]
  ): Map[GenericProcessingModule, TimeTriggeredScheduler | RoundRobinScheduler] =
    val schedulers: Set[TimeTriggeredScheduler | RoundRobinScheduler] =
      timeTrigSchedulers ++ roundRobinSchedulers
    processingElems
      .map(pe =>
        pe -> schedulers
          .find(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
          .get
      )
      .toMap
  end computeSchedulersFromPEs

  def computeTimeTriggeredPEs(model: ForSyDeModel)(using
      processingElems: Set[GenericProcessingModule],
      timeTrigSchedulers: Set[TimeTriggeredScheduler]
  ): Set[GenericProcessingModule] =
    processingElems.filter(pe =>
      timeTrigSchedulers.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeTimeTriggeredPEs

  def computeRoundRobinPEs(
      model: ForSyDeModel
  )(using
      processingElems: Set[GenericProcessingModule],
      roundRobinSchedulers: Set[RoundRobinScheduler]
  ): Set[GenericProcessingModule] =
    processingElems.filter(pe =>
      roundRobinSchedulers.exists(t => model.hasConnection(t, pe) || model.hasConnection(pe, t))
    )
  end computeRoundRobinPEs

//   def hasSchedulers(model: ForSyDeModel)

}
