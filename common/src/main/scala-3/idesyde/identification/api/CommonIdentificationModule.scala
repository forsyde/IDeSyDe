package idesyde.identification.api

import idesyde.identification.IdentificationRule
import idesyde.identification.rules.sdf.SDFAppIdentificationRule
import idesyde.identification.rules.reactor.ReactorMinusIdentificationRule
import idesyde.identification.rules.platform.NetworkedDigitalHWIdentRule
import idesyde.identification.rules.platform.SchedulableNetDigHWIdentRule
import idesyde.identification.rules.mixed.ReactorMinusAppDSEIdentRule
import idesyde.identification.rules.mixed.ReactorMinusAppDSEMznIdentRule
import idesyde.identification.rules.workload.PeriodicTaskIdentificationRule
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import org.apache.commons.math3.fraction.BigFraction
import idesyde.utils.BigFractionIsNumeric
import idesyde.identification.rules.mixed.PeriodicTaskToSchedHWIdentRule

class CommonIdentificationModule extends IdentificationModule {

  given Numeric[BigFraction] = BigFractionIsNumeric()

  def identificationRules = Set[IdentificationRule](
    SDFAppIdentificationRule(),
    ReactorMinusIdentificationRule(
      Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
    ),
    // ReactorMinusToJobsRule(),
    NetworkedDigitalHWIdentRule(),
    SchedulableNetDigHWIdentRule(),
    ReactorMinusAppDSEIdentRule(),
    ReactorMinusAppDSEMznIdentRule(),
    PeriodicTaskIdentificationRule(),
    PeriodicTaskToSchedHWIdentRule()
  )

}
