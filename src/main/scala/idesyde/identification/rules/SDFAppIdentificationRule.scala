package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.SdfApplication
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait

final case class SDFAppIdentificationRule()
    extends IdentificationRule[SdfApplication] {

  def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[SdfApplication]) = {
    val sdf_actors =
      model.vertexSet.stream.filter(v => v.hasTrait(VertexTrait.SDFComb))
    val sdf_delays =
      model.vertexSet.stream.filter(v => v.hasTrait(VertexTrait.SDFPrefix))
    (false, Option.empty)
  }
}
