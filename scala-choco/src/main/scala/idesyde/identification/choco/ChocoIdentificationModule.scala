package idesyde.identification.choco

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW2
import idesyde.identification.IdentificationModule
import spire.math.Rational
import idesyde.identification.choco.rules.ChocoRules
import idesyde.identification.DesignModel

class ChocoIdentificationModule() extends IdentificationModule {


  
  given Conversion[Double, Rational] = (d) => Rational(d)
  given Fractional[Rational]         = spire.compat.fractional[Rational]
  
  val identificationRules = Set(
    ChocoRules.identChocoSDFToSChedTileHW2
    )
    
  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = Set()
}
