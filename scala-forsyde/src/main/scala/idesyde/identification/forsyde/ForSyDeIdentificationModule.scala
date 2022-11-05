package idesyde.identification.forsyde

import java.util.concurrent.ThreadPoolExecutor

import idesyde.identification.forsyde.rules.sdf.SDFAppIdentificationRule
import idesyde.identification.forsyde.rules.platform.NetworkedDigitalHWIdentRule
import idesyde.identification.forsyde.rules.platform.SchedulableNetDigHWIdentRule
import idesyde.identification.forsyde.rules.workload.PeriodicWorkloadIdentificationRule
import idesyde.identification.forsyde.rules.mixed.PeriodicTaskToSchedHWIdentRule
import idesyde.identification.forsyde.rules.reactor.ReactorMinusIdentificationRule
import java.util.concurrent.Executors
import idesyde.identification.forsyde.models.platform.SchedulableTiledDigitalHardware
import idesyde.identification.forsyde.rules.platform.TiledDigitalHardwareIRule
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW
import idesyde.identification.IdentificationModule
import idesyde.identification.forsyde.models.mixed.SDFToExplicitSchedHW
import idesyde.identification.forsyde.rules.sdf.SDFRules
import spire.math.Rational
import spire.compat._
import spire.algebra._
import spire.math._
import idesyde.utils.Logger
import idesyde.identification.forsyde.rules.platform.PlatformRules
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel

class ForSyDeIdentificationModule(using Logger) extends IdentificationModule {

  given Conversion[Double, Rational] = (d) => Rational(d)
  given Conversion[Int, Rational]    = (i) => Rational(i)
  // given scala.math.Fractional[Rational] = fractional[Rational]

  val identificationRules = Set(
    SDFRules.identSDFApplication,
    PlatformRules.identTiledMultiCore,
    PlatformRules.identPartitionedCoresWithRuntimes
    // SDFAppIdentificationRule(),
    // NetworkedDigitalHWIdentRule(),
    // SchedulableNetDigHWIdentRule(),
    // // ReactorMinusAppDSEIdentRule(),
    // // ReactorMinusAppDSEMznIdentRule(),
    // PeriodicWorkloadIdentificationRule(),
    // PeriodicTaskToSchedHWIdentRule(),
    // // ReactorMinusIdentificationRule(
    // //   Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
    // // ),
    // TiledDigitalHardwareIRule(),
    // SchedulableTiledDigitalHardware.identFromAny,
    // SDFToSchedTiledHW.identFromAny,
    // SDFToExplicitSchedHW
  )

  val integrationRules = Set()

}
