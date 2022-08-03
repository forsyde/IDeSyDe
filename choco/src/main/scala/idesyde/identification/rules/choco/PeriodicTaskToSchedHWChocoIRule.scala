package idesyde.identification.rules.choco

import idesyde.identification.ForSyDeIdentificationRule
import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.models.choco.workload.PeriodicTaskToSchedHWChoco
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

class PeriodicTaskToSchedHWChocoIRule
    extends ForSyDeIdentificationRule[PeriodicTaskToSchedHWChoco] {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ) = {
    identified
      .find(model =>
        model match {
          case dse: PeriodicTaskToSchedHW => true
          case _                          => false
        }
      )
      .map(m => new IdentificationResult(true, Option(PeriodicTaskToSchedHWChoco(m.asInstanceOf[PeriodicTaskToSchedHW]))))
      .getOrElse(new IdentificationResult(false, Option.empty))
  }

}
