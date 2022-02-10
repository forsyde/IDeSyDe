package idesyde.identification.api

import idesyde.identification.IdentificationRule

class ChocoIdentificationModule() extends IdentificationModule {

  def identificationRules: Set[IdentificationRule] = Set()
}
