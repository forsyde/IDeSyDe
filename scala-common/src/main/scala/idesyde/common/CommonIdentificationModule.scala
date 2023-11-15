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
import idesyde.core.IdentificationRule
import idesyde.common.AnalysedSDFApplication
import idesyde.core.OpaqueDecisionModel
import java.{util => ju}

object CommonIdentificationModule
    extends StandaloneModule
    with MixedRules
    with PlatformRules
    with WorkloadRules
    with ApplicationRules {

  override def identificationRules(): ju.Set[IdentificationRule] = Set(
    IdentificationRule.OnlyCertainDecisionModels(
      identSchedulableTiledMultiCore,
      Set("PartitionedCoresWithRuntimes", "TiledMultiCoreWithFunctions")
    ),
    IdentificationRule.OnlyCertainDecisionModels(
      identPartitionedSharedMemoryMultiCore,
      Set("PartitionedCoresWithRuntimes", "SharedMemoryMultiCore")
    ),
    IdentificationRule.OnlyDecisionModels(identSDFToPartitionedSharedMemory),
    IdentificationRule.OnlyDecisionModels(identSDFToTiledMultiCore),
    IdentificationRule.OnlyCertainDecisionModels(
      identAnalysedSDFApplication,
      Set("SDFApplication", "SDFApplicationWithFunctions")
    ),
    IdentificationRule.OnlyDecisionModels(
      identPeriodicWorkloadToPartitionedSharedMultiCore
    ),
    IdentificationRule.OnlyDecisionModels(identTaksAndSDFServerToMultiCore),
    IdentificationRule.OnlyDecisionModels(identTiledFromShared),
    IdentificationRule.OnlyDecisionModels(identTaskdAndSDFServer),
    IdentificationRule.OnlyDecisionModels(identCommonSDFApplication),
    IdentificationRule.OnlyCertainDecisionModels(
      identAggregatedCommunicatingAndTriggeredReactiveWorkload,
      Set("CommunicatingAndTriggeredReactiveWorkload")
    )
  ).asJava

  def uniqueIdentifier: String = "CommonIdentificationModule"

  def main(args: Array[String]) = standaloneModule(args)

  override def fromOpaqueDecision(opaque: OpaqueDecisionModel): ju.Optional[DecisionModel] = {
    opaque.category() match {
      case "SDFApplicationWithFunctions" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[SDFApplicationWithFunctions](x))
          .or(() => opaque.bodyJson().map(x => read[SDFApplicationWithFunctions](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SDFApplication" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[SDFApplication](x))
          .or(() => opaque.bodyJson().map(x => read[SDFApplication](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "AnalysedSDFApplication" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[AnalysedSDFApplication](x))
          .or(() => opaque.bodyJson().map(x => read[AnalysedSDFApplication](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "TiledMultiCoreWithFunctions" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[TiledMultiCoreWithFunctions](x))
          .or(() => opaque.bodyJson().map(x => read[TiledMultiCoreWithFunctions](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PartitionedCoresWithRuntimes" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[PartitionedCoresWithRuntimes](x))
          .or(() => opaque.bodyJson().map(x => read[PartitionedCoresWithRuntimes](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SchedulableTiledMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[SchedulableTiledMultiCore](x))
          .or(() => opaque.bodyJson().map(x => read[SchedulableTiledMultiCore](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SDFToTiledMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[SDFToTiledMultiCore](x))
          .or(() => opaque.bodyJson().map(x => read[SDFToTiledMultiCore](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SharedMemoryMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[SharedMemoryMultiCore](x))
          .or(() => opaque.bodyJson().map(x => read[SharedMemoryMultiCore](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "CommunicatingAndTriggeredReactiveWorkload" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[CommunicatingAndTriggeredReactiveWorkload](x))
          .or(() => opaque.bodyJson().map(x => read[CommunicatingAndTriggeredReactiveWorkload](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PartitionedSharedMemoryMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[PartitionedSharedMemoryMultiCore](x))
          .or(() => opaque.bodyJson().map(x => read[PartitionedSharedMemoryMultiCore](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadAndSDFServers" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[PeriodicWorkloadAndSDFServers](x))
          .or(() => opaque.bodyJson().map(x => read[PeriodicWorkloadAndSDFServers](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadToPartitionedSharedMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          .or(() =>
            opaque.bodyJson().map(x => read[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          )
          .map(x => x.asInstanceOf[DecisionModel])
      case _ => None.toJava
    }
  }

}
