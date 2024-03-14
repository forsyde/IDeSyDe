package idesyde.forsydeio.legacy

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import org.virtuslab.yaml.*

import upickle.default._
import java.{util => ju}
import idesyde.core.IdentificationRule
import idesyde.core.IdentificationResult
import idesyde.core.ReverseIdentificationRule
import idesyde.forsydeio.legacy.MixedRules
import idesyde.forsydeio.legacy.SDFRules
import idesyde.forsydeio.legacy.PlatformRules
import idesyde.forsydeio.legacy.WorkloadRules
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.core.Module
import forsyde.io.core.ModelHandler
import idesyde.forsydeio.legacy.ForSyDeDesignModel
import java.nio.file.Paths
import idesyde.common.legacy.SDFToTiledMultiCore
import idesyde.common.legacy.PeriodicWorkloadToPartitionedSharedMultiCore
import java.nio.file.Files
import forsyde.io.bridge.sdf3.drivers.SDF3Driver
import forsyde.io.lib.hierarchy.ForSyDeHierarchy
import forsyde.io.lib.LibForSyDeModelHandler
import java.io.StringReader
import idesyde.common.legacy.AperiodicAsynchronousDataflow
import idesyde.core.OpaqueDesignModel
import idesyde.core.OpaqueDecisionModel
import idesyde.blueprints.StandaloneModule
import idesyde.common.legacy.SDFApplication
import idesyde.common.legacy.AnalysedSDFApplication
import idesyde.common.legacy.TiledMultiCoreWithFunctions
import idesyde.common.legacy.PartitionedCoresWithRuntimes
import idesyde.common.legacy.SchedulableTiledMultiCore
import idesyde.common.legacy.SharedMemoryMultiCore
import idesyde.common.legacy.CommunicatingAndTriggeredReactiveWorkload
import idesyde.common.legacy.PartitionedSharedMemoryMultiCore
import idesyde.common.legacy.PeriodicWorkloadAndSDFServers
import idesyde.devicetree.OSDescription
import idesyde.devicetree.identification.OSDescriptionDesignModel
import idesyde.devicetree.identification.CanParseDeviceTree
import idesyde.devicetree.identification.DeviceTreeDesignModel
import idesyde.choco.ChocoExplorer
import idesyde.common.legacy.PeriodicWorkloadAndSDFServerToMultiCoreOld

class ForSyDeIOScalaModule
    extends Module
    with idesyde.forsydeio.legacy.MixedRules
    with SDFRules
    with idesyde.forsydeio.legacy.PlatformRules
    with idesyde.forsydeio.legacy.WorkloadRules
    with idesyde.common.legacy.MixedRules
    with idesyde.common.legacy.PlatformRules
    with idesyde.common.legacy.WorkloadRules
    with idesyde.common.legacy.ApplicationRules
    with idesyde.devicetree.identification.PlatformRules
    with CanParseDeviceTree {

  def adaptIRuleToJava[T <: DecisionModel](
      func: (Set[DesignModel], Set[DecisionModel]) => (Set[T], Set[String])
  ): ju.function.BiFunction[ju.Set[? <: DesignModel], ju.Set[
    ? <: DecisionModel
  ], IdentificationResult] =
    (a: ju.Set[? <: DesignModel], b: ju.Set[? <: DecisionModel]) => {
      val (iden, msgs) = func(a.asScala.toSet, b.asScala.toSet)
      IdentificationResult(iden.asJava, msgs.asJava)
    }

  def adaptRevRuleToJava[T <: DesignModel](
      func: (Set[DecisionModel], Set[DesignModel]) => Set[T]
  ): ju.function.BiFunction[ju.Set[? <: DecisionModel], ju.Set[? <: DesignModel], ju.Set[
    ? <: DesignModel
  ]] =
    (a: ju.Set[? <: DecisionModel], b: ju.Set[? <: DesignModel]) => {
      func(a.asScala.toSet, b.asScala.toSet).map(_.asInstanceOf[DesignModel]).asJava
    }

   def fromOpaqueDecision(opaque: OpaqueDecisionModel): ju.Optional[DecisionModel] = {
    opaque.category() match {
      case "SDFToTiledMultiCore" =>
        opaque
          .bodyJson()
          .map(x => read[SDFToTiledMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadToPartitionedSharedMultiCore" =>
        opaque
          .bodyJson()
          .map(x => read[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "AperiodicAsynchronousDataflow" =>
        opaque
          .bodyJson()
          .map(x => read[AperiodicAsynchronousDataflow](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SDFApplication" =>
        opaque
          .bodyJson()
          .map(x => read[SDFApplication](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "AnalysedSDFApplication" =>
        opaque
          .bodyJson()
          .map(x => read[AnalysedSDFApplication](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "TiledMultiCoreWithFunctions" =>
        opaque
          .bodyJson()
          .map(x => read[TiledMultiCoreWithFunctions](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PartitionedCoresWithRuntimes" =>
        opaque
          .bodyJson()
          .map(x => read[PartitionedCoresWithRuntimes](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SchedulableTiledMultiCore" =>
        opaque
          .bodyJson()
          .map(x => read[SchedulableTiledMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "SharedMemoryMultiCore" =>
        opaque
          .bodyJson()
          .map(x => read[SharedMemoryMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "CommunicatingAndTriggeredReactiveWorkload" =>
        opaque
          .bodyJson()
          .map(x => read[CommunicatingAndTriggeredReactiveWorkload](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PartitionedSharedMemoryMultiCore" =>
        opaque
          .bodyJson()
          .map(x => read[PartitionedSharedMemoryMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadAndSDFServers" =>
        opaque
          .bodyJson()
          .map(x => read[PeriodicWorkloadAndSDFServers](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadAndSDFServerToMultiCoreOld" =>
        opaque
          .bodyJson()
          .map(x => read[PeriodicWorkloadAndSDFServerToMultiCoreOld](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case _ => ju.Optional.empty()
    }
  }

  val modelHandler = LibForSyDeModelHandler
    .registerLibForSyDe(ModelHandler())
    .registerDriver(SDF3Driver())
  // .registerDriver(new ForSyDeAmaltheaDriver())

  override def identificationRules(): ju.Set[IdentificationRule] = Set(
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identSharedMemoryMultiCoreFromDeviceTree)),
    IdentificationRule.OnlyDesignModels(
      adaptIRuleToJava(identPartitionedCoresWithRuntimesFromDeviceTree)
    ),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identSDFApplication)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identTiledMultiCore)),
    IdentificationRule.Generic(adaptIRuleToJava(identPartitionedCoresWithRuntimes)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identPeriodicDependentWorkload)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identSharedMemoryMultiCore)),
    IdentificationRule.Generic(
      adaptIRuleToJava(identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization)
    ),
    // IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identAperiodicDataflowFromSY)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identRuntimesAndProcessors)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identInstrumentedComputationTimes)),
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

  def identificationRulesCanonicalClassesNames(): Array[String] = identificationRules().asScala.map(cls => cls.getClass().getCanonicalName()).toArray

  override def reverseIdentificationRules(): ju.Set[ReverseIdentificationRule] = Set(
    ReverseIdentificationRule.Generic(
      adaptRevRuleToJava(integratePeriodicWorkloadToPartitionedSharedMultiCore)
    ),
    ReverseIdentificationRule.Generic(adaptRevRuleToJava(integrateSDFToTiledMultiCore)),
    ReverseIdentificationRule.Generic(
      adaptRevRuleToJava(integratePeriodicWorkloadAndSDFServerToMultiCoreOld)
    ),
  ).asJava

  override def explorers() = Set(ChocoExplorer()).asJava

  // def main(args: Array[String]): Unit =
  //   standaloneModule(args).ifPresent(javalin => javalin.start(0))

  def fromOpaqueDesign(opaque: OpaqueDesignModel): ju.Optional[DesignModel] = {
    if (modelHandler.canLoadModel(opaque.format())) {
      return opaque
        .asString()
        .flatMap(body => {
          try {
            ju.Optional
              .of(ForSyDeDesignModel(modelHandler.readModel(body, opaque.format())));
          } catch {
            case e: Exception =>
              e.printStackTrace();
              ju.Optional.empty();
          }
        });
    } else if (opaque.format() == "yaml") {
      opaque
        .asString()
        .flatMap(body =>
          body.as[OSDescription] match {
            case Right(value) => Some(OSDescriptionDesignModel(value)).asJava
            case Left(value)  => None.asJava
          };
        )
    } else if (opaque.format() == "dts") {
      {
        val root = ("""\w.dts""".r).findFirstIn(opaque.category()).getOrElse("")
        opaque
          .asString()
          .flatMap(body =>
            parseDeviceTreeWithPrefix(body, root) match {
              case Success(result, next) => Some(DeviceTreeDesignModel(List(result))).asJava
              case _                     => None.asJava
            }
          )
      }
    } else {
      return ju.Optional.empty();
    }
  }

  def uniqueIdentifier: String = "ForSyDeIOScalaModule"
}
