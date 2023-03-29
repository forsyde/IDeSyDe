package idesyde.identification.choco

import idesyde.core.DecisionModel
import idesyde.identification.choco.models.mixed.ChocoSDFToSChedTileHW2
import idesyde.core.IdentificationLibrary
import spire.math.Rational
import idesyde.identification.choco.rules.ChocoRules
import idesyde.core.DesignModel
import idesyde.utils.Logger

final class ChocoIdentificationLibrary(using Logger) extends IdentificationLibrary with ChocoRules {

  given Conversion[Double, Rational] = (d) => Rational(d)

  val identificationRules = Set(
    identChocoSDFToSChedTileHW2,
    identChocoComDepTasksToMultiCore
  )

  def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = Set()
}
