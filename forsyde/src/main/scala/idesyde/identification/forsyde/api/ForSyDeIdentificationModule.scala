package idesyde.identification.forsyde.api

import java.util.concurrent.ThreadPoolExecutor

import idesyde.identification.forsyde.rules.sdf.SDFAppIdentificationRule
import idesyde.identification.forsyde.rules.platform.NetworkedDigitalHWIdentRule
import idesyde.identification.forsyde.rules.platform.SchedulableNetDigHWIdentRule
import idesyde.identification.forsyde.rules.workload.PeriodicWorkloadIdentificationRule
import idesyde.identification.forsyde.rules.mixed.PeriodicTaskToSchedHWIdentRule
import idesyde.identification.forsyde.rules.reactor.ReactorMinusIdentificationRule
import java.util.concurrent.Executors
import idesyde.identification.forsyde.rules.platform.TiledDigitalHardwareIRule.apply
import idesyde.identification.forsyde.models.platform.SchedulableTiledDigitalHardware
import idesyde.identification.forsyde.rules.platform.TiledDigitalHardwareIRule
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW
import idesyde.identification.IdentificationModule
import idesyde.implicits.forsyde.given_Fractional_Rational
import idesyde.implicits.forsyde.given_Conversion_Double_Rational
import idesyde.identification.forsyde.models.mixed.SDFToExplicitSchedHW

class ForSyDeIdentificationModule extends IdentificationModule {

  val identificationRules = Set(
    SDFAppIdentificationRule(),
    // ReactorMinusToJobsRule(),
    NetworkedDigitalHWIdentRule(),
    SchedulableNetDigHWIdentRule(),
    // ReactorMinusAppDSEIdentRule(),
    // ReactorMinusAppDSEMznIdentRule(),
    PeriodicWorkloadIdentificationRule(),
    PeriodicTaskToSchedHWIdentRule(),
    ReactorMinusIdentificationRule(
      Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
    ),
    TiledDigitalHardwareIRule(),
    SchedulableTiledDigitalHardware.identFromAny,
    SDFToSchedTiledHW.identFromAny,
    SDFToExplicitSchedHW
  )

}
