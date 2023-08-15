package idesyde.forsydeio

import upickle.default._

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
import forsyde.io.core.ModelHandler
import idesyde.forsydeio.ForSyDeDesignModel
import java.nio.file.Paths
import os.Path
import idesyde.common.SDFToTiledMultiCore
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import java.nio.file.Files
import forsyde.io.bridge.sdf3.drivers.SDF3Driver
import forsyde.io.lib.ForSyDeHierarchy
import forsyde.io.lib.TraitNamesFrom0_6To0_7
import idesyde.blueprints.DecisionModelMessage
import idesyde.blueprints.DesignModelMessage
import java.io.StringReader

object ForSyDeIdentificationModule
    extends StandaloneIdentificationModule
    with MixedRules
    with SDFRules
    with PlatformRules
    with WorkloadRules {

  given Logger = logger

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[PeriodicWorkloadToPartitionedSharedMultiCore])
      case _ => None
    }
  }

  def decisionMessageToModel(m: DecisionModelMessage): Option[DecisionModel] = {
    m.header match {
      case DecisionModelHeader("SDFToTiledMultiCore", _, _) =>
        m.body.map(s => read[SDFToTiledMultiCore](s))
      case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", body_path, _) =>
        m.body.map(s => read[PeriodicWorkloadToPartitionedSharedMultiCore](s))
      case _ => None
    }
  }

  val modelHandler = ModelHandler()
    .registerDriver(SDF3Driver())
    .registerTraitHierarchy(ForSyDeHierarchy())
    .registerSystemGraphMigrator(TraitNamesFrom0_6To0_7())
  // .registerDriver(new ForSyDeAmaltheaDriver())

  val identificationRules = Set(
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSDFApplication),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identTiledMultiCore),
    identPartitionedCoresWithRuntimes,
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identPeriodicDependentWorkload),
    MarkedIdentificationRule.DesignModelOnlyIdentificationRule(identSharedMemoryMultiCore),
    identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization
  )

  val reverseIdentificationRules = Set(
    integratePeriodicWorkloadToPartitionedSharedMultiCore,
    integrateSDFToTiledMultiCore
  )

  def main(args: Array[String]): Unit = standaloneIdentificationModule(args)

  def designHeaderToModel(header: DesignModelHeader): Set[DesignModel] = {
    header match {
      case DesignModelHeader("ForSyDeDesignModel", model_paths, _) =>
        model_paths.flatMap(p => {
          modelHandler.canLoadModel(Paths.get(p)) match {
            case true =>
              Some(ForSyDeDesignModel(modelHandler.loadModel(p)))
            case false =>
              None
          }
        })
      case _ =>
        Set()
    }
  }

  override def inputsToDesignModel(p: os.Path): Option[DesignModelHeader | DesignModel] = {
    if (!p.last.startsWith("header") && modelHandler.canLoadModel(p.toNIO)) {
      val m = modelHandler.loadModel(p.toNIO)
      Some(ForSyDeDesignModel(m))
    } else {
      None
    }
  }

  override def designModelToOutput(m: DesignModel, p: Path): Option[Path] = m match {
    case ForSyDeDesignModel(systemGraph) =>
      if (os.isDir(p)) {
        Files.createDirectories(p.toNIO)
        var targetIdx = 0
        var target    = p / s"reversed_${targetIdx}_$uniqueIdentifier.fiodl"
        while (os.isFile(target) && os.exists(target)) {
          targetIdx += 1
          target = p / s"reversed_${targetIdx}_$uniqueIdentifier.fiodl"
        }
        modelHandler.writeModel(systemGraph, target.toNIO)
        Some(target)
      } else if (modelHandler.canWriteModel(p.toNIO)) {
        modelHandler.writeModel(systemGraph, p.toNIO)
        Some(p)
      } else None
    case _: DesignModel =>
      None
  }

  def designMessageToModel(message: DesignModelMessage): Set[DesignModel] = {
    // println(message.withEscapedNewLinesText)
    for (
      path_str <- message.header.model_paths;
      path = Paths.get(path_str);
      if modelHandler.canLoadModel(path);
      content <- message.body;
      ext_start = path.getFileName().toString().indexOf(".")
    )
      yield {
        // println(path.getFileName().toString().substring(ext_start + 1))
        val m = ForSyDeDesignModel(
          modelHandler.readModel(content, path.getFileName().toString().substring(ext_start + 1))
        )
        // println("sucess")
        m
      }
  }

  def uniqueIdentifier: String = "ForSyDeIdentificationModule"
}
