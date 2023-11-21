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
import idesyde.core.IdentificationResult
import java.util.function.BiFunction

object CommonModule
    extends StandaloneModule
    with MixedRules
    with PlatformRules
    with WorkloadRules
    with ApplicationRules {

  def adaptIRuleToJava[T <: DecisionModel](
      func: (Set[DesignModel], Set[DecisionModel]) => (Set[T], Set[String])
  ): BiFunction[ju.Set[? <: DesignModel], ju.Set[? <: DecisionModel], IdentificationResult] =
    (a, b) => {
      val (iden, msgs) = func(a.asScala.toSet, b.asScala.toSet)
      IdentificationResult(iden.asJava, msgs.asJava)
    }

  override def identificationRules(): ju.Set[IdentificationRule] = Set(
    IdentificationRule.OnlyCertainDecisionModels(
      adaptIRuleToJava(identSchedulableTiledMultiCore),
      Set("PartitionedCoresWithRuntimes", "TiledMultiCoreWithFunctions").asJava
    ),
    IdentificationRule.OnlyCertainDecisionModels(
      adaptIRuleToJava(identPartitionedSharedMemoryMultiCore),
      Set("PartitionedCoresWithRuntimes", "SharedMemoryMultiCore").asJava
    ),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identSDFToPartitionedSharedMemory)),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identSDFToTiledMultiCore)),
    IdentificationRule.OnlyCertainDecisionModels(
      adaptIRuleToJava(identAnalysedSDFApplication),
      Set("SDFApplication", "SDFApplicationWithFunctions").asJava
    ),
    IdentificationRule.OnlyDecisionModels(
      adaptIRuleToJava(identPeriodicWorkloadToPartitionedSharedMultiCore)
    ),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identTaksAndSDFServerToMultiCore)),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identTiledFromShared)),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identTaskdAndSDFServer)),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identCommonSDFApplication)),
    IdentificationRule.OnlyCertainDecisionModels(
      adaptIRuleToJava(identAggregatedCommunicatingAndTriggeredReactiveWorkload),
      Set("CommunicatingAndTriggeredReactiveWorkload").asJava
    )
  ).asJava

  def uniqueIdentifier: String = "CommonScalaModule"

  def main(args: Array[String]) = standaloneModule(args).ifPresent(javalin => javalin.start(0))

  override def fromOpaqueDecision(opaque: OpaqueDecisionModel): ju.Optional[DecisionModel] = {
    opaque.category() match {
      case "SDFApplicationWithFunctions" =>
        opaque
          .bodyJson().map(x => read[SDFApplicationWithFunctions](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SDFApplication" =>
        opaque
          .bodyJson().map(x => read[SDFApplication](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "AnalysedSDFApplication" =>
        opaque
          .bodyJson().map(x => read[AnalysedSDFApplication](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "TiledMultiCoreWithFunctions" =>
        opaque
          .bodyJson().map(x => read[TiledMultiCoreWithFunctions](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PartitionedCoresWithRuntimes" =>
        opaque
          .bodyJson().map(x => read[PartitionedCoresWithRuntimes](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SchedulableTiledMultiCore" =>
        opaque
          .bodyJson().map(x => read[SchedulableTiledMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SDFToTiledMultiCore" =>
        opaque
          .bodyJson().map(x => read[SDFToTiledMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SharedMemoryMultiCore" =>
        opaque
          .bodyJson().map(x => read[SharedMemoryMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "CommunicatingAndTriggeredReactiveWorkload" =>
        opaque
          .bodyJson().map(x => read[CommunicatingAndTriggeredReactiveWorkload](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PartitionedSharedMemoryMultiCore" =>
        opaque
          .bodyJson().map(x => read[PartitionedSharedMemoryMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadAndSDFServers" =>
        opaque
          .bodyJson().map(x => read[PeriodicWorkloadAndSDFServers](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadToPartitionedSharedMultiCore" =>
        opaque
          .bodyJson()
          .map(x => read[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case _ => None.toJava
    }
  }

}
