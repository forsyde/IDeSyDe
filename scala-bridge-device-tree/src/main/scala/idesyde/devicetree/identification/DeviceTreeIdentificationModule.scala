package idesyde.devicetree.identification

import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel

object DeviceTreeIdentificationModule extends IdentificationModule with PlatformRules {

  override def identificationRules
      : Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] = ???

  override def integrationRules: Set[(DesignModel, DecisionModel) => Option[? <: DesignModel]] = ???

}
