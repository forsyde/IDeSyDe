package idesyde.identification.api

import idesyde.identification.IdentificationRule
import idesyde.identification.rules.choco.PeriodicTaskToSchedHWChocoIRule
import idesyde.identification.DecisionModel

class ChocoIdentificationModule() extends IdentificationModule {

  def identificationRules: Set[IdentificationRule[? <: DecisionModel]] = Set(PeriodicTaskToSchedHWChocoIRule())
}
