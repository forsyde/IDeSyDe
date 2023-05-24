package idesyde.common

import upickle.default.*

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.Logger
import idesyde.blueprints.CanParseIdentificationModuleConfiguration
import idesyde.common.SDFApplication
import idesyde.common.TiledMultiCore
import idesyde.common.PartitionedCoresWithRuntimes
import idesyde.common.SchedulableTiledMultiCore
import idesyde.common.SDFToTiledMultiCore
import idesyde.common.SharedMemoryMultiCore
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.common.PartitionedSharedMemoryMultiCore
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.PeriodicWorkloadAndSDFServers
import idesyde.core.MarkedIdentificationRule

object CommonIdentificationModule
    extends IdentificationModule
    with CanParseIdentificationModuleConfiguration
    with MixedRules
    with PlatformRules
    with WorkloadRules {

  given Logger = logger

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def designHeaderToModel: Set[DesignModelHeader => Set[DesignModel]] = Set()

  val identificationRules = Set(
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identSchedulableTiledMultiCore,
      Set("PartitionedCoresWithRuntimes", "TiledMultiCore")
    ),
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identPartitionedSharedMemoryMultiCore,
      Set("PartitionedCoresWithRuntimes", "SharedMemoryMultiCore")
    ),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identSDFToPartitionedSharedMemory),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identSDFToTiledMultiCore),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(
      identPeriodicWorkloadToPartitionedSharedMultiCore
    ),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identTaksAndSDFServerToMultiCore),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identTiledFromShared),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identTaskdAndSDFServer),
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identAggregatedCommunicatingAndTriggeredReactiveWorkload,
      Set("CommunicatingAndTriggeredReactiveWorkload")
    )
  )

  val reverseIdentificationRules = Set()

  def uniqueIdentifier: String = "CommonIdentificationModule"

  def main(args: Array[String]) = standaloneIdentificationModule(args)

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFApplication", body_path, _) =>
        body_path.flatMap(decodeFromPath[SDFApplication])
      case DecisionModelHeader("TiledMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[TiledMultiCore])
      case DecisionModelHeader("PartitionedCoresWithRuntimes", body_path, _) =>
        body_path.flatMap(decodeFromPath[PartitionedCoresWithRuntimes])
      case DecisionModelHeader("SchedulableTiledMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[SchedulableTiledMultiCore])
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case DecisionModelHeader("SharedMemoryMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[SharedMemoryMultiCore])
      case DecisionModelHeader("CommunicatingAndTriggeredReactiveWorkload", body_path, _) =>
        body_path.flatMap(decodeFromPath[CommunicatingAndTriggeredReactiveWorkload])
      case DecisionModelHeader("PartitionedSharedMemoryMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[PartitionedSharedMemoryMultiCore])
      case DecisionModelHeader("PeriodicWorkloadAndSDFServers", body_path, _) =>
        body_path.flatMap(decodeFromPath[PeriodicWorkloadAndSDFServers])
      case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[PeriodicWorkloadToPartitionedSharedMultiCore])
      case _ => None
    }
  }

}
