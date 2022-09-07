package idesyde.identification.choco

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHWSlowest
import idesyde.identification.IdentificationModule
import idesyde.identification.choco.rules.PeriodicTaskToSchedHWChocoIRule

class ChocoIdentificationModule() extends IdentificationModule {

  def identificationRules: Set[IdentificationRule[? <: DecisionModel]] = Set(
    PeriodicTaskToSchedHWChocoIRule(),
    ChocoSDFToSChedTileHW,
    ChocoSDFToSChedTileHWSlowest
  )
}
