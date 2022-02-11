package idesyde.identification.rules.choco

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel

class PeriodicTaskToSchedHWChocoIRule extends IdentificationRule {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    identified
      .find(model =>
        model match {
          case dse: PeriodicTaskToSchedHW => true
          case _                          => false
        }
      )
      .map((true, Option(PeriodicTaskToSchedHWChoco(_))))
      .getOrElse((false, Option.empty))
  }

}
