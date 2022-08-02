package idesyde.identification.api

import org.apache.commons.math3.fraction.BigFraction
import idesyde.utils.BigFractionIsNumeric
import idesyde.utils.BigFractionIsIntegral
import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import java.util.concurrent.ThreadPoolExecutor

import idesyde.identification.rules.sdf.SDFAppIdentificationRule
import idesyde.identification.rules.platform.NetworkedDigitalHWIdentRule
import idesyde.identification.rules.platform.SchedulableNetDigHWIdentRule
import idesyde.identification.rules.workload.PeriodicWorkloadIdentificationRule
import idesyde.identification.rules.mixed.PeriodicTaskToSchedHWIdentRule
import idesyde.identification.rules.reactor.ReactorMinusIdentificationRule
import java.util.concurrent.Executors
import idesyde.utils.BigFractionIsMultipliableFractional
import idesyde.utils.MultipliableFractional
import idesyde.identification.rules.platform.TiledDigitalHardwareIRule.apply
import idesyde.identification.models.platform.SchedulableTiledDigitalHardware
import idesyde.identification.rules.platform.TiledDigitalHardwareIRule
import idesyde.identification.models.mixed.SDFToSchedTiledHW

class ForSyDeIdentificationModule extends IdentificationModule {

  given Integral[BigFraction]               = BigFractionIsIntegral()
  given MultipliableFractional[BigFraction] = BigFractionIsMultipliableFractional()
  given Conversion[Double, BigFraction]     = (f) => new BigFraction(f)

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
    SDFToSchedTiledHW.identFromAny
  )

}
