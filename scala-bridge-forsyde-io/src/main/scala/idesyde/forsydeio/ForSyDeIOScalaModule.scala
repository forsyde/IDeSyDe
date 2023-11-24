package idesyde.forsydeio

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import org.virtuslab.yaml.*

import upickle.default._
import java.{util => ju}
import idesyde.core.IdentificationRule
import idesyde.core.IdentificationResult
import idesyde.core.ReverseIdentificationRule
import idesyde.forsydeio.MixedRules
import idesyde.forsydeio.SDFRules
import idesyde.forsydeio.PlatformRules
import idesyde.forsydeio.WorkloadRules
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import forsyde.io.core.ModelHandler
import idesyde.forsydeio.ForSyDeDesignModel
import java.nio.file.Paths
import idesyde.common.SDFToTiledMultiCore
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import java.nio.file.Files
import forsyde.io.bridge.sdf3.drivers.SDF3Driver
import forsyde.io.lib.hierarchy.ForSyDeHierarchy
import forsyde.io.lib.LibForSyDeModelHandler
import java.io.StringReader
import idesyde.common.AperiodicAsynchronousDataflow
import idesyde.core.OpaqueDesignModel
import idesyde.core.OpaqueDecisionModel
import idesyde.blueprints.StandaloneModule
import idesyde.common.SDFApplication
import idesyde.common.AnalysedSDFApplication
import idesyde.common.TiledMultiCoreWithFunctions
import idesyde.common.PartitionedCoresWithRuntimes
import idesyde.common.SchedulableTiledMultiCore
import idesyde.common.SharedMemoryMultiCore
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.common.PartitionedSharedMemoryMultiCore
import idesyde.common.PeriodicWorkloadAndSDFServers
import idesyde.devicetree.OSDescription
import idesyde.devicetree.identification.OSDescriptionDesignModel
import idesyde.devicetree.identification.CanParseDeviceTree
import idesyde.devicetree.identification.DeviceTreeDesignModel

object ForSyDeIOScalaModule
    extends StandaloneModule
    with MixedRules
    with SDFRules
    with PlatformRules
    with WorkloadRules
    with ApplicationRules
    with idesyde.common.MixedRules
    with idesyde.common.PlatformRules
    with idesyde.common.WorkloadRules
    with idesyde.common.ApplicationRules
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

  override def fromOpaqueDecision(opaque: OpaqueDecisionModel): ju.Optional[DecisionModel] = {
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
      case _ => ju.Optional.empty()
    }
  }

  val modelHandler = LibForSyDeModelHandler
    .registerLibForSyDe(ModelHandler())
    .registerDriver(SDF3Driver())
  // .registerDriver(new ForSyDeAmaltheaDriver())

  override def identificationRules(): ju.Set[IdentificationRule] = Set(
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identSharedMemoryMultiCoreFromDeviceTree)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identPartitionedCoresWithRuntimesFromDeviceTree)),
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
    // IdentificationRule.OnlyCertainDecisionModels(
    //   adaptIRuleToJava(identAnalysedSDFApplication),
    //   Set("SDFApplication", "SDFApplicationWithFunctions").asJava
    // ),
    IdentificationRule.OnlyDecisionModels(
      adaptIRuleToJava(identPeriodicWorkloadToPartitionedSharedMultiCore)
    ),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identTaksAndSDFServerToMultiCore)),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identTiledFromShared)),
    IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identTaskdAndSDFServer)),
    // IdentificationRule.OnlyDecisionModels(adaptIRuleToJava(identCommonSDFApplication)),
    IdentificationRule.OnlyCertainDecisionModels(
      adaptIRuleToJava(identAggregatedCommunicatingAndTriggeredReactiveWorkload),
      Set("CommunicatingAndTriggeredReactiveWorkload").asJava
    )
  ).asJava

  override def reverseIdentificationRules(): ju.Set[ReverseIdentificationRule] = Set(
    ReverseIdentificationRule.Generic(
      adaptRevRuleToJava(integratePeriodicWorkloadToPartitionedSharedMultiCore)
    ),
    ReverseIdentificationRule.Generic(adaptRevRuleToJava(integrateSDFToTiledMultiCore))
  ).asJava

  def main(args: Array[String]): Unit =
    standaloneModule(args).ifPresent(javalin => javalin.start(0))

  override def fromOpaqueDesign(opaque: OpaqueDesignModel): ju.Optional[DesignModel] = {
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
      opaque.asString().flatMap(body => 
        body.as[OSDescription] match {
          case Right(value) => Some(OSDescriptionDesignModel(value)).asJava
          case Left(value)  => None.asJava
        };
      )
    } else if (opaque.format() == "dts") {
      {
        val root = ("""\w.dts""".r).findFirstIn(opaque.category()).getOrElse("")
        opaque.asString().flatMap(body => 
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
