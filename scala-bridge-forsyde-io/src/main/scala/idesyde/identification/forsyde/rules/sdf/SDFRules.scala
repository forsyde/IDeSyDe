package idesyde.identification.forsyde.rules.sdf

import scala.jdk.CollectionConverters._

import idesyde.utils.Logger
import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel
import idesyde.common.SDFApplicationWithFunctions
import scala.collection.mutable.Buffer
import scala.collection.mutable
import forsyde.io.lib.behavior.moc.sdf.SDFActor
import forsyde.io.core.SystemGraph
import forsyde.io.lib.implementation.functional.BufferLike
import forsyde.io.lib.behavior.moc.sdf.SDFChannel
import forsyde.io.lib.ForSyDeHierarchy

trait SDFRules {

  def identSDFApplication(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[SDFApplicationWithFunctions] = {
    val modelOpt = models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
    if (modelOpt.isEmpty) {
      return Set.empty
    }
    val model       = modelOpt.get
    var sdfActors   = Buffer.empty[SDFActor]
    var sdfChannels = Buffer.empty[SDFChannel]
    model.vertexSet.stream
      .forEach(v => {
        if (ForSyDeHierarchy.SDFActor.tryView(model, v).isPresent())
          sdfActors += ForSyDeHierarchy.SDFActor.tryView(model, v).get()
        //else if (SDFDelay.conforms(v)) sdfDelays = SDFDelay.enforce(v)
        else if (ForSyDeHierarchy.SDFChannel.tryView(model, v).isPresent())
          sdfChannels += ForSyDeHierarchy.SDFChannel.tryView(model, v).get()
      })
    val channelsConnectActors =
      sdfChannels.forall(c =>
        c.consumer().map(a => sdfActors.contains(a)).orElse(false)
          && c.producer().map(a => sdfActors.contains(a)).orElse(false)
      )
    if (sdfActors.size == 0 || !channelsConnectActors) {
      logger.debug("No actors, or channels do not connect actors")
      return Set.empty
    }
    var topologySrcs      = Buffer[String]()
    var topologyDsts      = Buffer[String]()
    var topologyEdgeValue = Buffer[Int]()
    sdfChannels.foreach(c => {
      c.producer()
        .ifPresent(src => {
          val rate = model
            .getAllEdges(src.getViewedVertex, c.getViewedVertex)
            .stream
            .mapToInt(e => {
              e.getSourcePort.map(sp => src.production().get(sp)).orElse(0)
            })
            .sum()
            .toInt
          // scribe.debug(s"adding ${src.getIdentifier()} -> ${c.getIdentifier()} : ${rate}")
          topologySrcs += src.getIdentifier()
          topologyDsts += c.getIdentifier()
          topologyEdgeValue += rate
        })
      c.consumer()
        .ifPresent(dst => {
          val rate = model
            .getAllEdges(c.getViewedVertex, dst.getViewedVertex)
            .stream
            .mapToInt(e => {
              e.getTargetPort.map(tp => dst.consumption().get(tp)).orElse(0)
            })
            .sum()
            .toInt
          // scribe.debug(s"adding ${c.getIdentifier()} -> ${dst.getIdentifier()} : ${rate}")
          topologySrcs += c.getIdentifier()
          topologyDsts += dst.getIdentifier()
          topologyEdgeValue += rate
        })
    })
    val processSizes = sdfActors.zipWithIndex
      .map((a, i) =>
        ForSyDeHierarchy.InstrumentedBehaviour
          .tryView(a)
          .map(_.maxSizeInBits().asInstanceOf[Long])
          .orElse(0L) +
          a.combFunctions()
            .stream()
            .mapToLong(fs =>
              ForSyDeHierarchy.InstrumentedBehaviour
                .tryView(fs)
                .map(_.maxSizeInBits().asInstanceOf[Long])
                .orElse(0L)
            )
            .sum
      )
      .toVector
    val processComputationalNeeds = sdfActors.map(fromSDFActorToNeeds(model, _)).toVector
    Set(
      SDFApplicationWithFunctions(
        sdfActors.map(_.getIdentifier()).toVector,
        sdfChannels.map(_.getIdentifier()).toVector,
        topologySrcs.toVector,
        topologyDsts.toVector,
        topologyEdgeValue.toVector,
        processSizes,
        processComputationalNeeds,
        sdfChannels.map(_.numInitialTokens().toInt).toVector,
        sdfChannels
          .map(ForSyDeHierarchy.BufferLike.tryView(_).map(_.elementSizeInBits().toLong).orElse(0L))
          .toVector,
        sdfActors.map(a => -1.0).toVector
      )
    )
  }

  private def fromSDFActorToNeeds(
      model: SystemGraph,
      actor: SDFActor
  ): Map[String, Map[String, Long]] = {
    // we do it mutable for simplicity...
    // the performance hit should not be a concern now, for super big instances, this can be reviewed
    var mutMap = mutable.Map[String, mutable.Map[String, Long]]()
    actor
      .combFunctions()
      .forEach(func => {
        ForSyDeHierarchy.InstrumentedBehaviour
          .tryView(func)
          .ifPresent(ifunc => {
            // now they have to be aggregated
            ifunc
              .computationalRequirements()
              .entrySet()
              .forEach(e => {
                if (mutMap.contains(e.getKey())) {
                  e.getValue()
                    .forEach((innerK, innerV) => {
                      mutMap(e.getKey())(innerK) = mutMap(e.getKey()).getOrElse(innerK, 0L) + innerV
                    })
                } else {
                  mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Long])
                }
              })
          })
      })
    // check also the actor, just in case, this might be best
    // in case the functions don't exist, but the actors is instrumented
    // anyway
    ForSyDeHierarchy.InstrumentedBehaviour
      .tryView(actor)
      .ifPresent(ia => {
        // now they have to be aggregated
        ia
          .computationalRequirements()
          .entrySet()
          .forEach(e => {
            if (mutMap.contains(e.getKey())) {
              e.getValue()
                .forEach((innerK, innerV) => {
                  mutMap(e.getKey())(innerK) = mutMap(e.getKey()).getOrElse(innerK, 0L) + innerV
                })
            } else {
              mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> v.asInstanceOf[Long])
            }
          })
      })
    mutMap.map((k, v) => k -> v.toMap).toMap
  }

}
