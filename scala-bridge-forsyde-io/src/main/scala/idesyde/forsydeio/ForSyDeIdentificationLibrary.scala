package idesyde.forsydeio

import java.util.concurrent.ThreadPoolExecutor

import java.util.concurrent.Executors
import idesyde.core.IdentificationLibrary
import idesyde.identification.forsyde.rules.sdf.SDFRules
import spire.math.Rational
import spire.compat._
import spire.algebra._
import spire.math._
import idesyde.utils.Logger
import idesyde.identification.forsyde.rules.PlatformRules
import idesyde.identification.forsyde.rules.WorkloadRules
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.core.MarkedIdentificationRule
import idesyde.identification.forsyde.rules.MixedRules

class ForSyDeIdentificationLibrary(using Logger)
    extends IdentificationLibrary
    with MixedRules
    with SDFRules
    with PlatformRules
    with WorkloadRules {

  val identificationRules = Set(
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSDFApplication),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identTiledMultiCore),
    identPartitionedCoresWithRuntimes,
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identPeriodicDependentWorkload),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSharedMemoryMultiCore),
    identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization
  )

  val reverseIdentificationRules = Set(
    integratePeriodicWorkloadToPartitionedSharedMultiCore,
    integrateSDFToTiledMultiCore
  )

}
