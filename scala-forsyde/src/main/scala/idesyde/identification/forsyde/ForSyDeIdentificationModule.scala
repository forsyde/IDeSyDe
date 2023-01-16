package idesyde.identification.forsyde

import java.util.concurrent.ThreadPoolExecutor

import idesyde.identification.forsyde.rules.sdf.SDFAppIdentificationRule
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
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel
import idesyde.identification.forsyde.rules.MixedRules

class ForSyDeIdentificationModule(using Logger) extends IdentificationModule {

  val identificationRules = Set(
    SDFRules.identSDFApplication,
    PlatformRules.identTiledMultiCore,
    PlatformRules.identPartitionedCoresWithRuntimes,
    WorkloadRules.identPeriodicDependentWorkload,
    PlatformRules.identSharedMemoryMultiCore,
    MixedRules.identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization
  )

  val integrationRules = Set(
    MixedRules.integratePeriodicWorkloadToPartitionedSharedMultiCore,
    MixedRules.integrateSDFToTiledMultiCore
  )

}
