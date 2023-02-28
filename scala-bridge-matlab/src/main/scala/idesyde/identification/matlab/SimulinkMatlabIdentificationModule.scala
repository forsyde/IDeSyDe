package idesyde.identification.matlab

import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel

object SimulinkMatlabIdentificationModule extends IdentificationModule {

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
  )

  override def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set(
    )

}
