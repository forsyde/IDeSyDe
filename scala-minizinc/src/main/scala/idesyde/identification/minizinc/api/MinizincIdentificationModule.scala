package idesyde.identification.minizinc.api

import idesyde.identification.DecisionModel

import idesyde.identification.IdentificationRule
import idesyde.identification.IdentificationModule
import idesyde.identification.DesignModel

class MinizincIdentificationModule extends IdentificationModule {

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Option[? <: DecisionModel]] = Set()

  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = Set()

}
