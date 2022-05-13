package idesyde.identification.rules.mixed

import idesyde.identification.IdentificationRule
import idesyde.identification.ForSyDeIdentificationRule
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSched

final case class ReactorMinusAppDSEMznIdentRule()
    extends ForSyDeIdentificationRule[ReactorMinusAppMapAndSchedMzn]:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[ForSyDeDecisionModel]
  ): (Boolean, Option[ForSyDeDecisionModel]) =
    val dseModelOpt = identified
      .find(_.isInstanceOf[ReactorMinusAppMapAndSched])
      .map(_.asInstanceOf[ReactorMinusAppMapAndSched])
    if (dseModelOpt.isDefined)
      val ForSyDeDecisionModel = ReactorMinusAppMapAndSchedMzn(sourceModel = dseModelOpt.get)
      (true, Option(ForSyDeDecisionModel))
    else if (ReactorMinusJobsDSEMznIdentRule.canIdentify(model, identified))
      (false, Option.empty)
    else
      (true, Option.empty)

end ReactorMinusAppDSEMznIdentRule

object ReactorMinusJobsDSEMznIdentRule:

  def canIdentify(
      model: ForSyDeSystemGraph,
      identified: Set[ForSyDeDecisionModel]
  ): Boolean = ReactorMinusAppDSEIdentRule.canIdentify(model, identified)

end ReactorMinusJobsDSEMznIdentRule
