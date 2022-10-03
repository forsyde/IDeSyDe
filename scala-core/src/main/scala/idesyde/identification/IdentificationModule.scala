package idesyde.identification

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

trait IdentificationModule {


  def identificationRules: Set[? <: Function2[Any, Set[DecisionModel], IdentificationResult[? <: DecisionModel]]]
}
