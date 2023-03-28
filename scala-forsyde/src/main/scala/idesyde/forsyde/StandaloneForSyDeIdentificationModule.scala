package idesyde.forsyde

import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.blueprints.StandaloneIdentificationModule
import idesyde.core.MarkedIdentificationRule
import idesyde.identification.forsyde.rules.MixedRules
import idesyde.identification.forsyde.rules.sdf.SDFRules
import idesyde.identification.forsyde.rules.PlatformRules
import idesyde.identification.forsyde.rules.WorkloadRules
import idesyde.utils.Logger
import idesyde.core.DecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader

object StandaloneForSyDeIdentificationModule
    extends StandaloneIdentificationModule
    with MixedRules
    with SDFRules
    with PlatformRules
    with WorkloadRules {

  given Logger = logger

  override def uniqueIdentifier: String = "ForSyDeIdentificationModule"

  override def designModelDecoders: Set[DesignModelHeader => Option[DesignModel]] = Set()

  override def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = Set()

  val identificationRules = Set(
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSDFApplication),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identTiledMultiCore),
    identPartitionedCoresWithRuntimes,
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(
      identPeriodicDependentWorkload
    ),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(
      identSharedMemoryMultiCore
    ),
    identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization
  )

  val integrationRules = Set(
    integratePeriodicWorkloadToPartitionedSharedMultiCore,
    integrateSDFToTiledMultiCore
  )

  def main(args: Array[String]): Unit = standaloneIdentificationModule(args)
}
