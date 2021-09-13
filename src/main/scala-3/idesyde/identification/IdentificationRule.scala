package idesyde.identification

import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.api.Identification
import org.slf4j.event.Level

trait IdentificationRule() {

  def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel])

}
