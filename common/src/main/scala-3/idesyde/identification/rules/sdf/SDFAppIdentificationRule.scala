package idesyde.identification.rules.sdf

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait
import idesyde.identification.models.sdf.SDFApplication

import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.moc.sdf.SDFComb
import forsyde.io.java.typed.viewers.moc.sdf.SDFDelay
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem

final case class SDFAppIdentificationRule() extends IdentificationRule with SDFQueriesMixin {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    var sdfActors   = Array.empty[SDFComb]
    var sdfDelays   = Array.empty[SDFDelay]
    var sdfChannels = Array.empty[SDFChannel]
    model.vertexSet.stream
      .filter(SDFElem.conforms(_))
      .forEach(v => {
        if (SDFComb.conforms(v)) sdfActors = sdfActors.appended(SDFComb.enforce(v))
        else if (SDFDelay.conforms(v)) sdfDelays = sdfDelays.appended(SDFDelay.enforce(v))
        else if (SDFChannel.conforms(v)) sdfChannels = sdfChannels.appended(SDFChannel.enforce(v))
      })
    if (sdfActors.size == 0 && sdfDelays.size == 0) {
      (true, Option.empty)
    } else {
      (false, Option.empty)
    }
  }
}
