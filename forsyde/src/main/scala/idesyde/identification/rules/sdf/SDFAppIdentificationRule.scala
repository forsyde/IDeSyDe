package idesyde.identification.rules.sdf

import scala.jdk.CollectionConverters.*

import idesyde.identification.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.VertexTrait
import idesyde.identification.models.sdf.SDFApplication

import java.util.stream.Collectors
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import org.antlr.v4.parse.BlockSetTransformer.topdown_return
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.Pseudograph
import org.jgrapht.graph.DefaultDirectedGraph
import forsyde.io.java.core.Vertex
import org.jgrapht.graph.WeightedPseudograph
import org.apache.commons.math3.fraction.BigFraction
import idesyde.identification.IdentificationResult

final case class SDFAppIdentificationRule()(using Integral[BigFraction])
    extends ForSyDeIdentificationRule[SDFApplication]
    with SDFQueriesMixin {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ) = {
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
        c.getConsumerPort(model).map(a => sdfActors.contains(a)).orElse(false)
          && c.getProducerPort(model).map(a => sdfActors.contains(a)).orElse(false)
      )

    lazy val topology = {
      val g = SimpleDirectedGraph.createBuilder[SDFActor | SDFChannel, Int](() => 0)
      sdfActors.foreach(g.addVertex(_))
      sdfChannels.foreach(c => {
        g.addVertex(c)
        c.getProducerPort(model)
          .ifPresent(src => {
            model
              .getAllEdges(src.getViewedVertex, c.getViewedVertex)
              .forEach(e => {
                g.addEdge(src, c, e.getSourcePort.map(sp => src.getProduction.get(sp)).orElse(0))
              })
          })
        c.getConsumerPort(model)
          .ifPresent(dst => {
            model
              .getAllEdges(c.getViewedVertex, dst.getViewedVertex)
              .forEach(e => {
                g.addEdge(dst, c, e.getTargetPort.map(tp => dst.getConsumption.get(tp)).orElse(0))
              })
          })
      })
      g.buildAsUnmodifiable
    }

    if (sdfActors.size > 0 && channelsConnectActors) {
      new IdentificationResult(
        true,
        Option(
          SDFApplication(sdfActors, sdfChannels, topology, sdfActors.map(_.getCombFunctionsPort(model).asScala.toArray))
        )
      )
    } else {
      scribe.debug("No actors, or channels do not connect actors")
      new IdentificationResult(true, Option.empty)
    }
  }
}

object SDFAppIdentificationRule {}
