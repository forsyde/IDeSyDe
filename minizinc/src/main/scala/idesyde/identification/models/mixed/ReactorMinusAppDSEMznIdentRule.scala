package idesyde.identification.minizinc.rules.mixed

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.IdentificationResult
import idesyde.identification.forsyde.rules.mixed.ReactorMinusAppDSEIdentRule
import idesyde.identification.forsyde.ForSyDeIdentificationRule
import idesyde.identification.forsyde.models.reactor.ReactorMinusAppMapAndSched
import idesyde.identification.minizinc.models.reactor.ReactorMinusAppMapAndSchedMzn

final case class ReactorMinusAppDSEMznIdentRule()
    extends ForSyDeIdentificationRule[ReactorMinusAppMapAndSchedMzn]:

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
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
      identified: scala.collection.Iterable[DecisionModel]
  ): Boolean = ReactorMinusAppDSEIdentRule.canIdentify(model, identified)

end ReactorMinusJobsDSEMznIdentRule
