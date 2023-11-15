package idesyde.forsydeio

import scala.jdk.CollectionConverters._

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

object ForSyDeIOScalaModule
    extends StandaloneModule
    with MixedRules
    with SDFRules
    with PlatformRules
    with WorkloadRules
    with ApplicationRules {

  def adaptIRuleToJava[T <: DecisionModel](
      func: (Set[DesignModel], Set[DecisionModel]) => (Set[T], Set[String])
  ): ju.function.BiFunction[ju.Set[? <: DesignModel], ju.Set[? <: DecisionModel], IdentificationResult] =
    (a: ju.Set[? <: DesignModel], b: ju.Set[? <: DecisionModel]) => {
      val (iden, msgs) = func(a.asScala.toSet, b.asScala.toSet)
      IdentificationResult(iden.asJava, msgs.asJava)
    }

  def adaptRevRuleToJava[T <: DesignModel](
      func: (Set[DecisionModel], Set[DesignModel]) => Set[T]
  ): ju.function.BiFunction[ju.Set[? <: DecisionModel], ju.Set[? <: DesignModel], ju.Set[DesignModel]] =
    (a: ju.Set[? <: DecisionModel], b: ju.Set[? <: DesignModel]) => {
      func(a.asScala.toSet, b.asScala.toSet).map(_.asInstanceOf[DesignModel]).asJava
    }

  override def fromOpaqueDecision(opaque: OpaqueDecisionModel): ju.Optional[DecisionModel] = {
    opaque.category() match {
      case "SDFToTiledMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[SDFToTiledMultiCore](x))
          .or(() => opaque.bodyJson().map(x => read[SDFToTiledMultiCore](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadToPartitionedSharedMultiCore" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          .or(() =>
            opaque.bodyJson().map(x => read[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          )
          .map(x => x.asInstanceOf[DecisionModel])
      case "AperiodicAsynchronousDataflow" =>
        opaque
          .bodyCBOR()
          .map(x => readBinary[AperiodicAsynchronousDataflow](x))
          .or(() => opaque.bodyJson().map(x => read[AperiodicAsynchronousDataflow](x)))
          .map(x => x.asInstanceOf[DecisionModel])
      case _ => ju.Optional.empty()
    }
  }

  val modelHandler = LibForSyDeModelHandler
    .registerLibForSyDe(ModelHandler())
    .registerDriver(SDF3Driver())
  // .registerDriver(new ForSyDeAmaltheaDriver())

  override def identificationRules(): ju.Set[IdentificationRule] = Set(
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identSDFApplication)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identTiledMultiCore)),
    IdentificationRule.Generic(adaptIRuleToJava(identPartitionedCoresWithRuntimes)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identPeriodicDependentWorkload)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identSharedMemoryMultiCore)),
    IdentificationRule.Generic(adaptIRuleToJava(identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization)),
    // IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identAperiodicDataflowFromSY)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identRuntimesAndProcessors)),
    IdentificationRule.OnlyDesignModels(adaptIRuleToJava(identInstrumentedComputationTimes))
  ).asJava

  override def reverseIdentificationRules(): ju.Set[ReverseIdentificationRule] = Set(
    ReverseIdentificationRule.Generic(adaptRevRuleToJava(integratePeriodicWorkloadToPartitionedSharedMultiCore)),
    ReverseIdentificationRule.Generic(adaptRevRuleToJava(integrateSDFToTiledMultiCore))
  ).asJava

  def main(args: Array[String]): Unit = standaloneModule(args)

  def fromOpaqueDesign(opaque: OpaqueDesignModel): ju.Optional[DesignModel] = {
    if (modelHandler.canLoadModel(opaque.format())) {
      return opaque
        .asString()
        .flatMap(body => {
          try {
            return ju.Optional.of(modelHandler.readModel(body, opaque.format()));
          } catch {
            case e: Exception =>
              e.printStackTrace();
              return ju.Optional.empty();
          }
        })
        .map(x => ForSyDeDesignModel(x));
    } else {
      return ju.Optional.empty();
    }
  }

  def uniqueIdentifier: String = "ForSyDeIOScalaModule"
}
