package idesyde.identification.forsyde.rules.sdf

import scala.jdk.CollectionConverters.*

import idesyde.identification.forsyde.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.VertexTrait
import idesyde.identification.forsyde.models.sdf.SDFApplication

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
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import spire.math.*
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

final case class SDFAppIdentificationRule()
// extends ForSyDeIdentificationRule[SDFApplication]
// with SDFQueriesMixin
{

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
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
    val channelsConnectActors =
      sdfChannels.forall(c =>
        c.getConsumerPort(model).map(a => sdfActors.contains(a)).orElse(false)
          && c.getProducerPort(model).map(a => sdfActors.contains(a)).orElse(false)
      )

    lazy val topology = {
      val g = SimpleDirectedWeightedGraph.createBuilder[SDFActor | SDFChannel, DefaultEdge](() =>
        DefaultEdge()
      )
      sdfActors.foreach(g.addVertex(_))
      sdfChannels.foreach(c => {
        g.addVertex(c)
        c.getProducerPort(model)
          .ifPresent(src => {
            val rate = model
              .getAllEdges(src.getViewedVertex, c.getViewedVertex)
              .stream
              .mapToInt(e => {
                e.getSourcePort.map(sp => src.getProduction.get(sp)).orElse(0)
              })
              .sum()
              .toInt
            // scribe.debug(s"adding ${src.getIdentifier()} -> ${c.getIdentifier()} : ${rate}")
            g.addEdge(src, c, rate.toDouble)
          })
        c.getConsumerPort(model)
          .ifPresent(dst => {
            val rate = model
              .getAllEdges(c.getViewedVertex, dst.getViewedVertex)
              .stream
              .mapToInt(e => {
                e.getTargetPort.map(tp => dst.getConsumption.get(tp)).orElse(0)
              })
              .sum()
              .toInt
            // scribe.debug(s"adding ${c.getIdentifier()} -> ${dst.getIdentifier()} : ${rate}")
            g.addEdge(c, dst, rate.toDouble)
          })
      })
      g.buildAsUnmodifiable()
    }

    if (sdfActors.size > 0 && channelsConnectActors) {
      scribe.debug(s"found a SDFApplication with ${sdfActors.size} actors and ${sdfChannels.size} channels")
      new IdentificationResult(
        true,
        Option(
          SDFApplication(
            sdfActors,
            sdfChannels,
            topology,
            sdfActors.map(_.getCombFunctionsPort(model).asScala.toArray)
          )
        )
      )
    } else {
      scribe.debug("No actors, or channels do not connect actors")
      new IdentificationResult(true, Option.empty)
    }
  }
}

object SDFAppIdentificationRule {}
