package idesyde.identification

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

trait IdentificationModule {

  def identificationRules: Set[
    Function2[DesignModel, Set[DecisionModel], IdentificationResult[? <: DecisionModel]]
  ]

  def explorationMergers: Set[Function2[DesignModel, DecisionModel, Option[? <: DesignModel]]]
}
