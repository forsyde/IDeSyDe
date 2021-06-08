package idesyde.identification.interfaces

import forsyde.io.java.core.ForSyDeModel

trait IdentificationRule[Out <: DecisionModel] {

  def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[Out])

}
