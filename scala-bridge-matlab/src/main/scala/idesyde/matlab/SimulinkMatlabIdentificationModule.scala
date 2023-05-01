package idesyde.matlab

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel

import upickle.default._

import idesyde.utils.Logger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import os.Path

object SimulinkMatlabIdentificationModule extends IdentificationModule with ApplicationRules {

  override def inputsToDesignModel(p: Path): Option[DesignModelHeader | DesignModel] = if (
    p.ext == "json" && !p.baseName.startsWith("header")
  ) {
    Some(read[SimulinkReactiveDesignModel](os.read(p)))
  } else None

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = None

  override def uniqueIdentifier: String = "SimulinkMatlabIdentificationModule"

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
    identCommunicatingAndTriggeredReactiveWorkload
  )

  override def reverseIdentificationRules
      : Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set(
    )

  def main(args: Array[String]) = standaloneIdentificationModule(args)

}
