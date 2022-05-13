package idesyde.identification.rules.sdf

import idesyde.identification.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.VertexTrait
import idesyde.identification.models.sdf.SDFApplication

import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import org.antlr.v4.parse.BlockSetTransformer.topdown_return

final case class SDFAppIdentificationRule()
    extends ForSyDeIdentificationRule[SDFApplication]
    with SDFQueriesMixin {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[ForSyDeDecisionModel]
  ): (Boolean, Option[ForSyDeDecisionModel]) = {
    var sdfActors   = Array.empty[SDFActor]
    var sdfChannels = Array.empty[SDFChannel]
    model.vertexSet.stream
      .filter(SDFElem.conforms(_))
      .forEach(v => {
        if (SDFActor.conforms(v)) sdfActors = sdfActors.appended(SDFActor.enforce(v))
        //else if (SDFDelay.conforms(v)) sdfDelays = sdfDelays.appended(SDFDelay.enforce(v))
        else if (SDFChannel.conforms(v)) sdfChannels = sdfChannels.appended(SDFChannel.enforce(v))
      })
    lazy val channelsConnectActors =
      sdfChannels.forall(c =>
        c.getConsumerPort(model).stream.anyMatch(a => sdfActors.contains(a))
          && c.getProducerPort(model).stream.anyMatch(a => sdfActors.contains(a))
      )
    lazy val topology =
      sdfChannels.map(c => {
        sdfActors.map(a => {
          // look for the edges between the two. There should be one at least.
          model
            .getAllEdges(a.getViewedVertex, c.getViewedVertex)
            .stream
            .mapToInt(e => {
              e.getSourcePort.map(sp => a.getProduction.get(sp)).orElse(0)
            })
            .sum
          // now the same for consumption
          model
            .getAllEdges(c.getViewedVertex, a.getViewedVertex)
            .stream
            .mapToInt(e => {
              e.getTargetPort.map(tp => -a.getConsumption.get(tp)).orElse(0)
            })
            .sum
        })
      })
    if (sdfActors.size > 0 && channelsConnectActors) {
      (
        true,
        Option(
          SDFApplication(sdfActors, sdfChannels, topology)
        )
      )
    } else {
      scribe.debug("No actors or channels do not connect actors")
      (false, Option.empty)
    }
  }
}

object SDFAppIdentificationRule {}
