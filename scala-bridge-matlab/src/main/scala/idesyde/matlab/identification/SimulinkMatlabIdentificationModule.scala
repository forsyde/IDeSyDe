package idesyde.matlab.identification

import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel

import idesyde.matlab.identification.ApplicationRules
import idesyde.utils.Logger

class SimulinkMatlabIdentificationModule(using Logger)
    extends IdentificationModule
    with ApplicationRules {

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
    identCommunicatingAndTriggeredReactiveWorkload
  )

  override def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set(
    )

}
