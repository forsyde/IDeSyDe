package idesyde.identification.api

import idesyde.identification.IdentificationRule

trait IdentificationModule {
  def identificationRules: Set[IdentificationRule[?, ?]]
}
