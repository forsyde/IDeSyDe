package idesyde.identification.minizinc

import idesyde.core.DecisionModel

import idesyde.blueprints.IdentificationModule
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader

class MinizincIdentificationModule extends IdentificationModule {
  
  def designModelDecoders: Set[DesignModelHeader => Set[DesignModel]] = Set()
  
  def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = Set()

  def uniqueIdentifier: String = "MinizincIdentificationModule"

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    Set()

  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = Set()

}
