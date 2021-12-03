package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import idesyde.identification.models.SDFApplication
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait
import forsyde.io.java.typed.viewers.SDFComb
import forsyde.io.java.typed.viewers.SDFPrefix
import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.SDFSignal

final case class SDFAppIdentificationRule()
    extends IdentificationRule:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    val sdfActors =
      model.vertexSet.stream
        .filter(SDFComb.conforms(_))
        .map(SDFComb.safeCast(_).get)
        .collect(Collectors.toSet)
    val sdfDelays =
      model.vertexSet.stream
        .filter(SDFPrefix.conforms(_))
        .map(SDFPrefix.safeCast(_).get)
        .collect(Collectors.toSet)
    val sdfSignals = model.vertexSet.stream
        .filter(SDFSignal.conforms(_))
        .map(SDFSignal.safeCast(_).get)
        .collect(Collectors.toSet)
    if (sdfActors.size == 0 && sdfDelays.size == 0) {
      (true, Option.empty)
    } else {

      (false, Option.empty)
    }
  }

end SDFAppIdentificationRule

object SDFAppIdentificationRule:

  def signalsAlwaysConnect(model: ForSyDeSystemGraph, sdfSignals: Seq[SDFSignal]): Boolean =
    sdfSignals.forall(s => 
      s.getFifoInPort(model).isPresent && s.getFifoOutPort(model).isPresent
      )

end SDFAppIdentificationRule
