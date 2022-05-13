package idesyde.identification

import forsyde.io.java.core.ForSyDeSystemGraph
import java.util.concurrent.ThreadPoolExecutor
import idesyde.identification.ForSyDeDecisionModel

trait IdentificationRule[M <: DecisionModel] {

  type G

  def identifyUntyped(model: Any,  identified: Set[? <: DecisionModel]): (Boolean, Option[M]) =
    model match {
      case m: G => identify(m, identified)
      case _ => (true, Option.empty)
    }

  def identify(
      model: G,
      identified: Set[? <: DecisionModel]
  ): (Boolean, Option[M])

}
