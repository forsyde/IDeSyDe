package idesyde.identification.minizinc

import idesyde.identification.DecisionModel

import idesyde.identification.IdentificationModule
import idesyde.identification.DesignModel

class MinizincIdentificationModule extends IdentificationModule {

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    Set()

  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = Set()

}
