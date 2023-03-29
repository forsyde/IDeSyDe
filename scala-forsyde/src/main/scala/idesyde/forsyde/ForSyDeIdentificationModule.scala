package idesyde.forsyde

import idesyde.identification.forsyde.ForSyDeIdentificationLibrary
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
import idesyde.identification.forsyde.ForSyDeDesignModel
import java.nio.file.Paths

object ForSyDeIdentificationModule
    extends IdentificationModule {

  given Logger = logger

  val commonIdentificationLibrary = CommonIdentificationLibrary()
  val forSyDeIdentificationLibrary = ForSyDeIdentificationLibrary()

  def uniqueIdentifier: String = "ForSyDeIdentificationModule"

  def designModelDecoders: Set[DesignModelHeader => Set[DesignModel]] = Set(
    decodeForSyDeDesignModelFromHeader
  )

  def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = Set()

  val identificationRules = forSyDeIdentificationLibrary.identificationRules ++ commonIdentificationLibrary.identificationRules

  val integrationRules = forSyDeIdentificationLibrary.integrationRules ++ commonIdentificationLibrary.integrationRules

  def main(args: Array[String]): Unit = standaloneIdentificationModule(args)

  def decodeForSyDeDesignModelFromHeader(header: DesignModelHeader): Set[DesignModel] = {
    val modelHandler = ForSyDeModelHandler()
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
}
