package idesyde.identification.rules.choco

import idesyde.identification.ForSyDeIdentificationRule
import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.models.choco.PeriodicTaskToSchedHWChoco
import idesyde.identification.DecisionModel

class PeriodicTaskToSchedHWChocoIRule extends ForSyDeIdentificationRule[PeriodicTaskToSchedHWChoco] {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[PeriodicTaskToSchedHWChoco]) = {
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
