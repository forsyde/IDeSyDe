package idesyde.common

import upickle.default.*

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.Logger
import idesyde.identification.common.CommonIdentificationLibrary
import idesyde.blueprints.CanParseIdentificationModuleConfiguration
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.platform.TiledMultiCore
import idesyde.identification.common.models.platform.PartitionedCoresWithRuntimes
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.common.models.platform.SharedMemoryMultiCore

object CommonIdentificationModule
    extends IdentificationModule
    with CanParseIdentificationModuleConfiguration {

  given Logger = logger

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def designHeaderToModel: Set[DesignModelHeader => Set[DesignModel]] = Set()

  private val commonIdentificationLibrary = CommonIdentificationLibrary()

  def reverseIdentificationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    commonIdentificationLibrary.reverseIdentificationRules

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    commonIdentificationLibrary.identificationRules

  def uniqueIdentifier: String = "CommonIdentificationModule"

  def main(args: Array[String]) = standaloneIdentificationModule(args)

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFApplication", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SDFApplication])
      case DecisionModelHeader("TiledMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[TiledMultiCore])
      case DecisionModelHeader("PartitionedCoresWithRuntimes", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[PartitionedCoresWithRuntimes])
      case DecisionModelHeader("SchedulableTiledMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SchedulableTiledMultiCore])
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case DecisionModelHeader("SharedMemoryMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SharedMemoryMultiCore])
      case _ => None
    }
  }

}
