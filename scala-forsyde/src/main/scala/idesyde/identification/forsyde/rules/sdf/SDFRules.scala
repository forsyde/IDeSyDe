package idesyde.identification.forsyde.rules.sdf

import idesyde.utils.Logger
import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.identification.common.models.sdf.SDFApplication
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel

object SDFRules {
  
    def identSDFApplication(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
    )(using Logger): SDFApplication = {
        val model = models
            .filter(_.isInstanceOf[ForSyDeDesignModel])
            .map(_.asInstanceOf[ForSyDeDesignModel])
            .map(_.systemGraph)
            .reduce(_.merge(_))
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
            val g = SimpleDirectedWeightedGraph.createBuilder[SDFActor | SDFChannel, DefaultEdge](() => DefaultEdge())
            sdfActors.foreach(g.addVertex(_))
            sdfChannels.foreach(c => {
                g.addVertex(c)
                c.getProducerPort(model)
                .ifPresent(src => {
                    val rate = model
                    .getAllEdges(src.getViewedVertex, c.getViewedVertex)
                    .stream.mapToInt(e => {
                        e.getSourcePort.map(sp => src.getProduction.get(sp)).orElse(0)
                    }).sum().toInt
                    // scribe.debug(s"adding ${src.getIdentifier()} -> ${c.getIdentifier()} : ${rate}")
                    g.addEdge(src, c, rate.toDouble)
                })
                c.getConsumerPort(model)
                .ifPresent(dst => {
                    val rate = model
                    .getAllEdges(c.getViewedVertex, dst.getViewedVertex)
                    .stream.mapToInt(e => {
                        e.getTargetPort.map(tp => dst.getConsumption.get(tp)).orElse(0)
                    }).sum().toInt
                    // scribe.debug(s"adding ${c.getIdentifier()} -> ${dst.getIdentifier()} : ${rate}")
                    g.addEdge(c, dst, rate.toDouble)
                })
            })
            g.buildAsUnmodifiable()
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
}
