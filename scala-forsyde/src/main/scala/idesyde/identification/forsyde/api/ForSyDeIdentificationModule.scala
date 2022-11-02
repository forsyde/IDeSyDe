package idesyde.identification.forsyde.api

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
import spire.math.Rational
import spire.compat._
import spire.algebra._
import spire.math._

class ForSyDeIdentificationModule extends IdentificationModule {

  given Conversion[Double, Rational] = (d) => Rational(d)
  given Conversion[Int, Rational]    = (i) => Rational(i)
  // given scala.math.Fractional[Rational] = fractional[Rational]

  val identificationRules = Set(
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

}
