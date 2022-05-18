package idesyde.identification.rules.choco

import idesyde.identification.IdentificationRule
import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.models.choco.PeriodicTaskToSchedHWChoco

class PeriodicTaskToSchedHWChocoIRule extends IdentificationRule {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[ForSyDeDecisionModel]) = {
    identified
      .find(model =>
        model match {
          case dse: PeriodicTaskToSchedHW => true
          case _                          => false
        }
      )
      .map(m => (true, Option(PeriodicTaskToSchedHWChoco(m.asInstanceOf[PeriodicTaskToSchedHW]))))
      .getOrElse((false, Option.empty))
  }

}
