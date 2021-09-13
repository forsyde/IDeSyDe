package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import idesyde.identification.models.reactor.ReactorMinusJobsMapAndSchedMzn
import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.models.reactor.ReactorMinusJobsMapAndSched

final case class ReactorMinusJobsDSEMznIdentRule()
    extends IdentificationRule:

  def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    val dseModelOpt = identified
      .find(_.isInstanceOf[ReactorMinusJobsMapAndSched])
      .map(_.asInstanceOf[ReactorMinusJobsMapAndSched])
    if (dseModelOpt.isDefined)
      val decisionModel = ReactorMinusJobsMapAndSchedMzn(sourceModel = dseModelOpt.get)
      (true, Option(decisionModel))
    else if (ReactorMinusJobsDSEMznIdentRule.canIdentify(model, identified))
      (false, Option.empty)
    else
      (true, Option.empty)

end ReactorMinusJobsDSEMznIdentRule

object ReactorMinusJobsDSEMznIdentRule:

  def canIdentify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): Boolean = ReactorMinusJobsDSEIdentRule.canIdentify(model, identified)

end ReactorMinusJobsDSEMznIdentRule
