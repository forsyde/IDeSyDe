package idesyde.matlab.identification

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel

import idesyde.matlab.identification.ApplicationRules
import idesyde.utils.Logger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader

object SimulinkMatlabIdentificationModule extends IdentificationModule with ApplicationRules {

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = None

  override def uniqueIdentifier: String = "SimulinkMatlabIdentificationModule"

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
    identCommunicatingAndTriggeredReactiveWorkload
  )

  override def reverseIdentificationRules
      : Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set(
    )

}
