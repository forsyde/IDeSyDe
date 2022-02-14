package idesyde.identification.api

import idesyde.identification.IdentificationRule
import idesyde.identification.rules.choco.PeriodicTaskToSchedHWChocoIRule

class ChocoIdentificationModule() extends IdentificationModule {

  def identificationRules: Set[IdentificationRule] = Set(PeriodicTaskToSchedHWChocoIRule())
}
