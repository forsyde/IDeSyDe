package idesyde.common

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.Logger
import idesyde.identification.common.CommonIdentificationLibrary
import idesyde.blueprints.CanParseIdentificationModuleConfiguration

object CommonIdentificationModule extends IdentificationModule with CanParseIdentificationModuleConfiguration {

  given Logger = logger

  val commonIdentificationLibrary = CommonIdentificationLibrary()

  def designModelDecoders: Set[DesignModelHeader => Set[DesignModel]] = Set()
    
  def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = Set()

  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = commonIdentificationLibrary.integrationRules

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = commonIdentificationLibrary.identificationRules

  def uniqueIdentifier: String = "CommonIdentificationModule"

  def main(args: Array[String]) = standaloneIdentificationModule(args)
  
}
