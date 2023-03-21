package idesyde.devicetree.identification

import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel
import idesyde.utils.Logger

class DeviceTreeIdentificationModule(using Logger) extends IdentificationModule with PlatformRules {

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = Set(
    identSharedMemoryMultiCore
  )

  override def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] =
    Set()

}
