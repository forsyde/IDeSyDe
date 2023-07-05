package idesyde.minizinc

import idesyde.core.DecisionModel

import idesyde.blueprints.StandaloneIdentificationModule
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader

class MinizincIdentificationModule extends StandaloneIdentificationModule {

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = None

  def uniqueIdentifier: String = "MinizincIdentificationModule"

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    Set()

  def reverseIdentificationRules
      : Set[(Set[DecisionModel], Set[DesignModel]) => Set[? <: DesignModel]] =
    Set()

}
