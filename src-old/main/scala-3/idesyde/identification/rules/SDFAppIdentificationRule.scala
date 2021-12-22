package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import idesyde.identification.models.SDFApplication
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait
import forsyde.io.java.typed.viewers.SDFComb
import forsyde.io.java.typed.viewers.SDFPrefix
import java.util.stream.Collectors

final case class SDFAppIdentificationRule()
    extends IdentificationRule {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    val sdf_actors =
      model.vertexSet.stream
        .filter(SDFComb.conforms(_))
        .map(SDFComb.safeCast(_).get)
        .collect(Collectors.toSet)
    val sdf_delays =
      model.vertexSet.stream
        .filter(SDFPrefix.conforms(_))
        .map(SDFPrefix.safeCast(_).get)
        .collect(Collectors.toSet)
    if (sdf_actors.size == 0 && sdf_delays.size == 0) {
      (true, Option.empty)
    } else {
      (false, Option.empty)
    }
  }
}
