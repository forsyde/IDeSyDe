package idesyde.identification.api

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel

trait IdentificationModule {
  def identificationRules: Set[IdentificationRule[? <: DecisionModel]]
}
