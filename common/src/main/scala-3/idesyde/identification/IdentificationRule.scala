package idesyde.identification

import forsyde.io.java.core.ForSyDeSystemGraph
import java.util.concurrent.ThreadPoolExecutor

trait IdentificationRule() {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel])

}
