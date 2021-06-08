package identification.rules

import identification.interfaces.IdentificationRule
import identification.models.SDFApplication
import forsyde.io.java.core.ForSyDeModel
import identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait

final case class SDFAppIdentificationRule() extends IdentificationRule[SDFApplication] {


    def identify(model: ForSyDeModel, identified: Set[DecisionModel]): (Boolean, Option[SDFApplication]) = {
        val sdf_actors = model.vertexSet.stream.filter(v => v.hasTrait(VertexTrait.SDFComb))
        val sdf_delays = model.vertexSet.stream.filter(v => v.hasTrait(VertexTrait.SDFPrefix))
        (false, Option.empty)
    }
}