package idesyde.identification.rules.choco

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel

class PeriodicTaskToSchedHWChocoRule() extends IdentificationRule {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = ???

}
