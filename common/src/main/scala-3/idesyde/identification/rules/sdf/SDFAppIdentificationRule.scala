package idesyde.identification.rules.sdf

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait
import idesyde.identification.models.sdf.SDFApplication

import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.moc.sdf.SDFComb
import forsyde.io.java.typed.viewers.moc.sdf.SDFDelay

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
        .filter(SDFDelay.conforms(_))
        .map(SDFDelay.safeCast(_).get)
        .collect(Collectors.toSet)
    if (sdf_actors.size == 0 && sdf_delays.size == 0) {
      (true, Option.empty)
    } else {
      (false, Option.empty)
    }
  }
}
