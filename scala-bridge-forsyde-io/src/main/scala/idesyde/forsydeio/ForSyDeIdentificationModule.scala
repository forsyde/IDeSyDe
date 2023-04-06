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

object ForSyDeIdentificationModule extends IdentificationModule {

  def decisionHeaderToModel(m: DecisionModelHeader): Seq[DecisionModel] = Seq()

  given Logger = logger

  val forSyDeIdentificationLibrary = ForSyDeIdentificationLibrary()

  val modelHandler = ForSyDeModelHandler()

  val identificationRules = forSyDeIdentificationLibrary.identificationRules

  val integrationRules = forSyDeIdentificationLibrary.integrationRules

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
    if (modelHandler.canLoadModel(p.toNIO)) {
      val m = modelHandler.loadModel(p.toNIO)
      Some(ForSyDeDesignModel(m))
    } else {
      None
    }
  }

  def uniqueIdentifier: String = "ForSyDeIdentificationModule"
}
