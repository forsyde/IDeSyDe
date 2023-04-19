package idesyde.devicetree

import idesyde.blueprints.IdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.utils.Logger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.devicetree.identification.PlatformRules

object DeviceTreeIdentificationModule extends IdentificationModule with PlatformRules {

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = None

  def decodeDesignModels: Set[DesignModelHeader => Set[DesignModel]] = Set()

  def uniqueIdentifier: String = "DeviceTreeIdentificationModule"

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    Set(
      identSharedMemoryMultiCore,
      identPartitionedCoresWithRuntimes
    )

  def reverseIdentificationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set()

}
