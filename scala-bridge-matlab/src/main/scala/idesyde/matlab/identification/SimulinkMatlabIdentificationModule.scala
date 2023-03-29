package idesyde.matlab.identification

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel

import idesyde.matlab.identification.ApplicationRules
import idesyde.utils.Logger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader

object SimulinkMatlabIdentificationModule
    extends IdentificationModule
    with ApplicationRules {

  override def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = Set()

  override def designModelDecoders: Set[DesignModelHeader => Set[DesignModel]] = Set()

  override def uniqueIdentifier: String =  "SimulinkMatlabIdentificationModule"

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
    identCommunicatingAndTriggeredReactiveWorkload
  )

  override def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set(
    )

}
