package idesyde.forsydeio

import idesyde.forsydeio.ForSyDeIdentificationLibrary
import idesyde.blueprints.IdentificationModule
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
import idesyde.identification.common.CommonIdentificationLibrary
import forsyde.io.java.drivers.ForSyDeModelHandler
import idesyde.forsydeio.ForSyDeDesignModel
import java.nio.file.Paths
import os.Path
import forsyde.io.java.sdf3.drivers.ForSyDeSDF3Driver
import forsyde.io.java.amalthea.drivers.ForSyDeAmaltheaDriver
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore

object ForSyDeIdentificationModule extends IdentificationModule {

  given Logger = logger

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case _ => None
    }
  }

  val forSyDeIdentificationLibrary = ForSyDeIdentificationLibrary()

  val modelHandler = ForSyDeModelHandler()
    .registerDriver(new ForSyDeSDF3Driver())
    .registerDriver(new ForSyDeAmaltheaDriver())

  val identificationRules = forSyDeIdentificationLibrary.identificationRules

  val reverseIdentificationRules = forSyDeIdentificationLibrary.reverseIdentificationRules

  def main(args: Array[String]): Unit = standaloneIdentificationModule(args)

  def designHeaderToModel(header: DesignModelHeader): Set[DesignModel] = {
    header match {
      case DesignModelHeader("ForSyDeDesignModel", model_paths, _, _) =>
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

  override def designModelToOutput(m: DesignModel, p: Path): Boolean = m match {
    case ForSyDeDesignModel(systemGraph) =>
      if (os.isDir(p)) {
        var targetIdx = 0
        var target    = p / s"design_model_output_$targetIdx.fiodl"
        while (os.isFile(target)) {
          targetIdx += 1
        }
        modelHandler.writeModel(systemGraph, target.toNIO)
      } else if (modelHandler.canWriteModel(p.toNIO)) {
        modelHandler.writeModel(systemGraph, p.toNIO)
        return true
      }
      false
    case _: DesignModel =>
      false
  }

  def uniqueIdentifier: String = "ForSyDeIdentificationModule"
}
