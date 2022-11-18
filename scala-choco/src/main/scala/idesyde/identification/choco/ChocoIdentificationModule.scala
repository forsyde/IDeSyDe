package idesyde.identification.choco

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW2
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHWSlowest
import idesyde.identification.IdentificationModule
import idesyde.identification.choco.rules.PeriodicTaskToSchedHWChocoIRule
import spire.math.Rational

class ChocoIdentificationModule() extends IdentificationModule {

  given Conversion[Double, Rational] = (d) => Rational(d)
  given Fractional[Rational]         = spire.compat.fractional[Rational]

  val identificationRules = Set(
    PeriodicTaskToSchedHWChocoIRule(),
    ChocoSDFToSChedTileHW.identifyFromAny,
    ChocoSDFToSChedTileHWSlowest.identifyFromAny,
    ChocoSDFToSChedTileHW2.identifyFromAny
  )
}
