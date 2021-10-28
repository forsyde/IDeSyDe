package idesyde.identification

import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.api.Identification
import java.util.concurrent.ThreadPoolExecutor

trait IdentificationRule() {

  def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel])

}
