package idesyde.identification.minizinc.api

import idesyde.identification.DecisionModel

import idesyde.identification.IdentificationRule
import idesyde.identification.IdentificationModule

class MinizincIdentificationModule extends IdentificationModule {

  def identificationRules: Set[IdentificationRule[? <: DecisionModel]] = Set()

}
