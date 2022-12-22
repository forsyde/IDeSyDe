package idesyde.identification.choco.rules

import idesyde.identification.forsyde.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.choco.models.workload.PeriodicTaskToSchedHWChoco
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.forsyde.models.mixed.PeriodicTaskToSchedHW

class PeriodicTaskToSchedHWChocoIRule
    extends ForSyDeIdentificationRule[PeriodicTaskToSchedHWChoco] {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ) = {
    identified
      .find(model =>
        model match {
          case dse: PeriodicTaskToSchedHW => true
          case _                          => false
        }
      )
      .map(m =>
        IdentificationResult.fixed(PeriodicTaskToSchedHWChoco(m.asInstanceOf[PeriodicTaskToSchedHW]))
      )
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

}
