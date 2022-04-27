package idesyde.identification.rules.sdf

import idesyde.identification.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait
import idesyde.identification.models.sdf.SDFApplication

import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.moc.sdf.SDFComb
import forsyde.io.java.typed.viewers.moc.sdf.SDFDelay
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem

final case class SDFAppIdentificationRule()
    extends ForSyDeIdentificationRule[SDFApplication]
    with SDFQueriesMixin {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    var sdfActors   = Array.empty[SDFComb]
    var sdfChannels = Array.empty[SDFChannel]
    model.vertexSet.stream
      .filter(SDFElem.conforms(_))
      .forEach(v => {
        if (SDFComb.conforms(v)) sdfActors = sdfActors.appended(SDFComb.enforce(v))
        //else if (SDFDelay.conforms(v)) sdfDelays = sdfDelays.appended(SDFDelay.enforce(v))
        else if (SDFChannel.conforms(v)) sdfChannels = sdfChannels.appended(SDFChannel.enforce(v))
      })
    lazy val channelsConnectActors =
      sdfChannels.forall(c =>
        c.getConsumerPort(model).stream.map(a => sdfActors.contains(a)).orElse(false)
          && c.getProducerPort(model).stream.map(a => sdfActors.contains(a)).orElse(false)
      )
    if (sdfActors.size > 0 && channelsConnectActors) {
      (true, Option.empty)
    } else {
      (false, Option.empty)
    }
  }
}

object SDFAppIdentificationRule {}
