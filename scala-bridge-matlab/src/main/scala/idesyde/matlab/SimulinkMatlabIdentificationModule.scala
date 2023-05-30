package idesyde.matlab

import idesyde.blueprints.StandaloneIdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel

import upickle.default._

import idesyde.utils.Logger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import os.Path

object SimulinkMatlabIdentificationModule
    extends StandaloneIdentificationModule
    with ApplicationRules {

  override def inputsToDesignModel(p: Path): Option[DesignModelHeader | DesignModel] = if (
    p.ext == "slx"
  ) {
    val res  = os.proc("matlab", "-batch", s"export_to_xxxxxxx('$p')").call()
    val dest = res.out.lines().last
    Some(read[SimulinkReactiveDesignModel](os.read(os.pwd / dest)))
  } else None

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = None

  override def uniqueIdentifier: String = "SimulinkMatlabIdentificationModule"

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
    identCommunicatingAndTriggeredReactiveWorkload
  )

  override def reverseIdentificationRules
      : Set[(Set[DecisionModel], Set[DesignModel]) => Set[? <: DesignModel]] =
    Set(
    )

  def main(args: Array[String]) = standaloneIdentificationModule(args)

}
