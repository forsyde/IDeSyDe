package idesyde.identification.rules.mixed

import idesyde.identification.IdentificationRule
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSched

final case class ReactorMinusAppDSEMznIdentRule()
    extends IdentificationRule:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    val dseModelOpt = identified
      .find(_.isInstanceOf[ReactorMinusAppMapAndSched])
      .map(_.asInstanceOf[ReactorMinusAppMapAndSched])
    if (dseModelOpt.isDefined)
      val decisionModel = ReactorMinusAppMapAndSchedMzn(sourceModel = dseModelOpt.get)
      (true, Option(decisionModel))
    else if (ReactorMinusJobsDSEMznIdentRule.canIdentify(model, identified))
      (false, Option.empty)
    else
      (true, Option.empty)

end ReactorMinusAppDSEMznIdentRule

object ReactorMinusJobsDSEMznIdentRule:

  def canIdentify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): Boolean = ReactorMinusAppDSEIdentRule.canIdentify(model, identified)

end ReactorMinusJobsDSEMznIdentRule
