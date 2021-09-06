package idesyde.identification.interfaces

import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.api.Identification
import org.slf4j.event.Level

trait IdentificationRule[Out <: DecisionModel]() {

  def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[Out])

}
