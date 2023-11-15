package idesyde.common

import scala.jdk.OptionConverters._
import scala.jdk.CollectionConverters._

import upickle.default.*

import idesyde.blueprints.StandaloneModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.common.SDFApplicationWithFunctions
import idesyde.common.TiledMultiCoreWithFunctions
import idesyde.common.PartitionedCoresWithRuntimes
import idesyde.common.SchedulableTiledMultiCore
import idesyde.common.SDFToTiledMultiCore
import idesyde.common.SharedMemoryMultiCore
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.common.PartitionedSharedMemoryMultiCore
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.PeriodicWorkloadAndSDFServers
import idesyde.core.MarkedIdentificationRule
import idesyde.common.AnalysedSDFApplication
import idesyde.core.OpaqueDecisionModel
import java.{util => ju}

object CommonIdentificationModule
    extends StandaloneModule
    with MixedRules
    with PlatformRules
    with WorkloadRules
    with ApplicationRules {

  val identificationRules = Set(
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identSchedulableTiledMultiCore,
      Set("PartitionedCoresWithRuntimes", "TiledMultiCoreWithFunctions")
    ),
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identPartitionedSharedMemoryMultiCore,
      Set("PartitionedCoresWithRuntimes", "SharedMemoryMultiCore")
    ),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identSDFToPartitionedSharedMemory),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identSDFToTiledMultiCore),
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identAnalysedSDFApplication,
      Set("SDFApplication", "SDFApplicationWithFunctions")
    ),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(
      identPeriodicWorkloadToPartitionedSharedMultiCore
    ),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identTaksAndSDFServerToMultiCore),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identTiledFromShared),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identTaskdAndSDFServer),
    MarkedIdentificationRule.DecisionModelOnlyIdentificationRule(identCommonSDFApplication),
    MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule(
      identAggregatedCommunicatingAndTriggeredReactiveWorkload,
      Set("CommunicatingAndTriggeredReactiveWorkload")
    )
  )

  val reverseIdentificationRules = Set()

  def uniqueIdentifier: String = "CommonIdentificationModule"

  def main(args: Array[String]) = standaloneIdentificationModule(args)

  override def fromOpaqueDecision(opaque: OpaqueDecisionModel): ju.Optional[DecisionModel] =
    m match {
      case DecisionModelHeader("SDFApplicationWithFunctions", body_path, _) =>
        body_path.flatMap(decodeFromPath[SDFApplicationWithFunctions])
      case DecisionModelHeader("SDFApplication", body_path, _) =>
        body_path.flatMap(decodeFromPath[SDFApplication])
      case DecisionModelHeader("AnalysedSDFApplication", body_path, _) =>
        body_path.flatMap(decodeFromPath[AnalysedSDFApplication])
      case DecisionModelHeader("TiledMultiCoreWithFunctions", body_path, _) =>
        body_path.flatMap(decodeFromPath[TiledMultiCoreWithFunctions])
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

  def decisionMessageToModel(m: DecisionModelMessage): Option[DecisionModel] = {
    m.header match {
      case DecisionModelHeader("SDFApplicationWithFunctions", body_path, _) =>
        m.body.map(s => read[SDFApplicationWithFunctions](s))
      case DecisionModelHeader("SDFApplication", body_path, _) =>
        m.body.map(s => read[SDFApplication](s))
      case DecisionModelHeader("AnalysedSDFApplication", body_path, _) =>
        m.body.map(s => read[AnalysedSDFApplication](s))
      case DecisionModelHeader("TiledMultiCoreWithFunctions", body_path, _) =>
        m.body.map(s => read[TiledMultiCoreWithFunctions](s))
      case DecisionModelHeader("PartitionedCoresWithRuntimes", body_path, _) =>
        m.body.map(s => read[PartitionedCoresWithRuntimes](s))
      case DecisionModelHeader("SchedulableTiledMultiCore", body_path, _) =>
        m.body.map(s => read[SchedulableTiledMultiCore](s))
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _) =>
        m.body.map(s => read[SDFToTiledMultiCore](s))
      case DecisionModelHeader("SharedMemoryMultiCore", body_path, _) =>
        m.body.map(s => read[SharedMemoryMultiCore](s))
      case DecisionModelHeader("RuntimesAndProcessors", body_path, _) =>
        m.body.map(s => read[RuntimesAndProcessors](s))
      case DecisionModelHeader("CommunicatingAndTriggeredReactiveWorkload", body_path, _) =>
        m.body.map(s => read[CommunicatingAndTriggeredReactiveWorkload](s))
      case DecisionModelHeader("PartitionedSharedMemoryMultiCore", body_path, _) =>
        m.body.map(s => read[PartitionedSharedMemoryMultiCore](s))
      case DecisionModelHeader("PeriodicWorkloadAndSDFServers", body_path, _) =>
        m.body.map(s => read[PeriodicWorkloadAndSDFServers](s))
      case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", body_path, _) =>
        m.body.map(s => read[PeriodicWorkloadToPartitionedSharedMultiCore](s))
      case _ => None
    }
  }

  override def decisionModelSchemas: Vector[String] = Vector(
  )

}
