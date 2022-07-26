package idesyde.identification.rules.mixed

import idesyde.identification.IdentificationRule
import idesyde.identification.ForSyDeIdentificationRule
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import idesyde.identification.DecisionModel
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSched
import idesyde.identification.IdentificationResult

final case class ReactorMinusAppDSEMznIdentRule()
    extends ForSyDeIdentificationRule[ReactorMinusAppMapAndSchedMzn]:

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ) =
    val dseModelOpt = identified
      .find(_.isInstanceOf[ReactorMinusAppMapAndSched])
      .map(_.asInstanceOf[ReactorMinusAppMapAndSched])
    if (dseModelOpt.isDefined)
      val ForSyDeDecisionModel = ReactorMinusAppMapAndSchedMzn(sourceModel = dseModelOpt.get)
      new IdentificationResult(true, Option(ForSyDeDecisionModel))
    else if (ReactorMinusJobsDSEMznIdentRule.canIdentify(model, identified))
      new IdentificationResult(false, Option.empty)
    else
      new IdentificationResult(true, Option.empty)

end ReactorMinusAppDSEMznIdentRule

object ReactorMinusJobsDSEMznIdentRule:

  def canIdentify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): Boolean = ReactorMinusAppDSEIdentRule.canIdentify(model, identified)

end ReactorMinusJobsDSEMznIdentRule
