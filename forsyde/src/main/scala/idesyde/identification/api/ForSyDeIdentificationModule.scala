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

class ForSyDeIdentificationModule extends IdentificationModule {
    
  given Numeric[BigFraction] = BigFractionIsNumeric()
  given Integral[BigFraction] = BigFractionIsIntegral()

  val identificationRules: Set[IdentificationRule[? <: DecisionModel]] = Set(
    SDFAppIdentificationRule(),
    // ReactorMinusToJobsRule(),
    NetworkedDigitalHWIdentRule(),
    SchedulableNetDigHWIdentRule(),
    // ReactorMinusAppDSEIdentRule(),
    // ReactorMinusAppDSEMznIdentRule(),
    PeriodicWorkloadIdentificationRule(),
    PeriodicTaskToSchedHWIdentRule(),
    ReactorMinusIdentificationRule(Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor])
  )
  
}
