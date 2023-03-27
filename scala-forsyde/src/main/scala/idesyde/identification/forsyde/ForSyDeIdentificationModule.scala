package idesyde.identification.forsyde

import java.util.concurrent.ThreadPoolExecutor

import java.util.concurrent.Executors
import idesyde.identification.IdentificationModule
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
import idesyde.identification.forsyde.rules.MixedRules
import idesyde.core.MarkedIdentificationRule.DesignModelOnlyIdentificationRule
import idesyde.core.MarkedIdentificationRule

class ForSyDeIdentificationModule(using Logger) extends IdentificationModule {

  val identificationRules = Set(
    DesignModelOnlyIdentificationRule(SDFRules.identSDFApplication),
    DesignModelOnlyIdentificationRule(PlatformRules.identTiledMultiCore),
    PlatformRules.identPartitionedCoresWithRuntimes,
    DesignModelOnlyIdentificationRule(WorkloadRules.identPeriodicDependentWorkload),
    DesignModelOnlyIdentificationRule(PlatformRules.identSharedMemoryMultiCore),
    MixedRules.identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization
  )

  val integrationRules = Set(
    MixedRules.integratePeriodicWorkloadToPartitionedSharedMultiCore,
    MixedRules.integrateSDFToTiledMultiCore
  )

}
