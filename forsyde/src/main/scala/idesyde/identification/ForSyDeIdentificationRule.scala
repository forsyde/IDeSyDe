package idesyde.identification

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph

trait ForSyDeIdentificationRule[M <: ForSyDeDecisionModel] extends IdentificationRule[M] {

  def identify[DesignModel](
      model: DesignModel,
      identified: Set[DecisionModel]
  ): IdentificationResult[M] =
    model match {
      case fio: ForSyDeSystemGraph =>
        new IdentificationResult(identifyFromForSyDe(fio, identified))
      case _ =>
        new IdentificationResult(false, Option.empty)
    }

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[M])
}
