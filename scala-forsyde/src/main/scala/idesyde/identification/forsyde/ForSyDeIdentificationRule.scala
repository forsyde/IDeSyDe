package idesyde.identification.forsyde

import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.IdentificationResult
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationRule

trait ForSyDeIdentificationRule[M <: ForSyDeDecisionModel] extends IdentificationRule[M] {

  def identify[DesignModel](
      model: DesignModel,
      identified: scala.collection.Iterable[DecisionModel]
  ): IdentificationResult[M] =
    model match {
      case fio: ForSyDeSystemGraph =>
        identifyFromForSyDe(fio, identified)
      case _ =>
        new IdentificationResult(false, Option.empty)
    }

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ): IdentificationResult[M]
}

object ForSyDeIdentificationRule {

  def identifyWrapper[M <: DecisionModel](
      model: Any,
      identified: scala.collection.Iterable[DecisionModel],
      irule: (ForSyDeSystemGraph, scala.collection.Iterable[DecisionModel]) => IdentificationResult[M]
  ): IdentificationResult[M] =
    model match {
      case fio: ForSyDeSystemGraph =>
        irule(fio, identified)
      case _ =>
        new IdentificationResult(false, Option.empty)
    }
}