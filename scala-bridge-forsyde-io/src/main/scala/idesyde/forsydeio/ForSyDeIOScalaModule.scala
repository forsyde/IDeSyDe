package idesyde.forsydeio

import upickle.default._

import idesyde.core.IdentificationRule
import idesyde.forsydeio.MixedRules
import idesyde.forsydeio.SDFRules
import idesyde.forsydeio.PlatformRules
import idesyde.forsydeio.WorkloadRules
import idesyde.core.DecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import forsyde.io.core.ModelHandler
import idesyde.forsydeio.ForSyDeDesignModel
import java.nio.file.Paths
import os.Path
import idesyde.common.SDFToTiledMultiCore
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import java.nio.file.Files
import forsyde.io.bridge.sdf3.drivers.SDF3Driver
import forsyde.io.lib.hierarchy.ForSyDeHierarchy
import forsyde.io.lib.LibForSyDeModelHandler
import idesyde.blueprints.DecisionModelMessage
import idesyde.blueprints.DesignModelMessage
import java.io.StringReader
import idesyde.common.AperiodicAsynchronousDataflow
import idesyde.core.OpaqueDesignModel
import idesyde.core.OpaqueDecisionModel

object ForSyDeIOScalaModule
    extends StandaloneModule
    with MixedRules
    with SDFRules
    with PlatformRules
    with WorkloadRules
    with ApplicationRules {

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
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSDFApplication),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identTiledMultiCore),
    identPartitionedCoresWithRuntimes,
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identPeriodicDependentWorkload),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSharedMemoryMultiCore),
    identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization,
    // MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identAperiodicDataflowFromSY),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identRuntimesAndProcessors)
    // MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identInstrumentedComputationTimes)
  ).asJava

  override def reverseIdentificationRules(): ju.Set[ReverseIdentificationRule] = Set(
    integratePeriodicWorkloadToPartitionedSharedMultiCore,
    integrateSDFToTiledMultiCore
  ).asJava

  def main(args: Array[String]): Unit = standaloneIdentificationModule(args)

  def fromOpaqueDesign(opaque: OpaqueDesignModel): ju.Optional[DesignModel] = {
    if (modelHandler.canLoadModel(opaque.format())) {
      return opaque
        .asString()
        .flatMap(body -> {
          try {
            return ju.Optional.of(modelHandler.readModel(body, opaque.format()));
          } catch (Exception e) {
            e.printStackTrace();
            return ju.Optional.empty();
          }
        })
        .map(ForSyDeIODesignModel(_));
    } else {
      return ju.Optional.empty();
    }
  }

  def uniqueIdentifier: String = "ForSyDeIOScalaModule"
}
