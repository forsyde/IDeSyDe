package idesyde.identification.minizinc

import idesyde.core.DecisionModel

import idesyde.identification.IdentificationModule
import idesyde.core.DesignModel

class MinizincIdentificationModule extends IdentificationModule {

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    Set()

  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = Set()

}
