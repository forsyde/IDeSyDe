package idesyde.identification.forsyde.rules.sdf

import scala.jdk.CollectionConverters._

import idesyde.utils.Logger
import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel
import idesyde.identification.common.models.sdf.SDFApplication
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import scala.collection.mutable.Buffer
import scala.collection.mutable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.impl.TokenizableDataBlock

trait SDFRules {

  def identSDFApplication(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[SDFApplication] = {
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
      .filter(SDFElem.conforms(_))
      .forEach(v => {
        if (SDFActor.conforms(v)) sdfActors += SDFActor.enforce(v)
        //else if (SDFDelay.conforms(v)) sdfDelays = SDFDelay.enforce(v)
        else if (SDFChannel.conforms(v)) sdfChannels += SDFChannel.enforce(v)
      })
    val channelsConnectActors =
      sdfChannels.forall(c =>
        c.getConsumerPort(model).map(a => sdfActors.contains(a)).orElse(false)
          && c.getProducerPort(model).map(a => sdfActors.contains(a)).orElse(false)
      )
    if (sdfActors.size == 0 || !channelsConnectActors) {
      logger.debug("No actors, or channels do not connect actors")
      return Set.empty
    }
    var topologySrcs      = Buffer[String]()
    var topologyDsts      = Buffer[String]()
    var topologyEdgeValue = Buffer[Int]()
    sdfChannels.foreach(c => {
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
          topologySrcs += src.getIdentifier()
          topologyDsts += c.getIdentifier()
          topologyEdgeValue += rate
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
          topologySrcs += c.getIdentifier()
          topologyDsts += dst.getIdentifier()
          topologyEdgeValue += rate
        })
    })
    val processSizes = sdfActors.zipWithIndex
      .map((a, i) =>
        InstrumentedExecutable.safeCast(a).map(_.getSizeInBits().asInstanceOf[Long]).orElse(0L) +
          a.getCombFunctionsPort(model)
            .stream()
            .mapToLong(fs =>
              InstrumentedExecutable
                .safeCast(fs)
                .map(_.getSizeInBits().asInstanceOf[Long])
                .orElse(0L)
            )
            .sum
      )
      .toVector
    val processComputationalNeeds = sdfActors.map(fromSDFActorToNeeds(model, _)).toVector
    Set(
      SDFApplication(
        sdfActors.map(_.getIdentifier()).toVector,
        sdfChannels.map(_.getIdentifier()).toVector,
        topologySrcs.toVector,
        topologyDsts.toVector,
        topologyEdgeValue.toVector,
        processSizes,
        processComputationalNeeds,
        sdfChannels.map(_.getNumOfInitialTokens().toInt).toVector,
        sdfChannels
          .map(TokenizableDataBlock.safeCast(_).map(_.getTokenSizeInBits().toLong).orElse(0L))
          .toVector,
        sdfActors.map(a => -1.0).toVector
      )
    )
  }

  private def fromSDFActorToNeeds(
      model: ForSyDeSystemGraph,
      actor: SDFActor
  ): Map[String, Map[String, Long]] = {
    // we do it mutable for simplicity...
    // the performance hit should not be a concern now, for super big instances, this can be reviewed
    var mutMap = mutable.Map[String, mutable.Map[String, Long]]()
    actor
      .getCombFunctionsPort(model)
      .forEach(func => {
        InstrumentedExecutable
          .safeCast(func)
          .ifPresent(ifunc => {
            // now they have to be aggregated
            ifunc
              .getOperationRequirements()
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
    InstrumentedExecutable
      .safeCast(actor)
      .ifPresent(ia => {
        // now they have to be aggregated
        ia
          .getOperationRequirements()
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
