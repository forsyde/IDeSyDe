package idesyde.identification.forsyde.rules

import scala.jdk.CollectionConverters._

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.platform.TiledMultiCore
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import idesyde.identification.forsyde.ForSyDeDesignModel
import forsyde.io.java.typed.viewers.platform.DigitalModule
import scala.collection.mutable.Buffer
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import scala.collection.mutable
import spire.math.Rational

object PlatformRules {

  def identTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[TiledMultiCore] = {
    val modelOpt = models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
    if (modelOpt.isEmpty) {
      return Option.empty
    }
    val model                 = modelOpt.get
    var processingElements    = Array.empty[GenericProcessingModule]
    var memoryElements        = Array.empty[GenericMemoryModule]
    var communicationElements = Array.empty[GenericCommunicationModule]
    model.vertexSet.stream
      .filter(v => DigitalModule.conforms(v))
      .forEach(v => {
        GenericProcessingModule
          .safeCast(v)
          .ifPresent(p => processingElements :+= p)
        GenericMemoryModule
          .safeCast(v)
          .ifPresent(p => memoryElements :+= p)
        GenericCommunicationModule
          .safeCast(v)
          .ifPresent(p => communicationElements :+= p)
      })
    if (
      processingElements.length <= 0 &&
      processingElements.size > memoryElements.size &&
      processingElements.size > communicationElements.size
    ) {
      return Option.empty
    }
    // check if pes and mes connect only to CE etc
    val processingOnlyValidLinks = processingElements.forall(pe => {
      model
        .outgoingEdgesOf(pe.getViewedVertex)
        .stream
        .map(model.getEdgeTarget(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
      &&
      model
        .incomingEdgesOf(pe.getViewedVertex)
        .stream
        .map(model.getEdgeSource(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
    })
    if (!processingOnlyValidLinks) {
      return Option.empty
    }
    // do the same for MEs
    val memoryOnlyValidLinks = memoryElements.forall(me => {
      model
        .outgoingEdgesOf(me.getViewedVertex)
        .stream
        .map(model.getEdgeTarget(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v =>
          GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
        )
      &&
      model
        .incomingEdgesOf(me.getViewedVertex)
        .stream
        .map(model.getEdgeSource(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v =>
          GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
        )
    })
    if (!memoryOnlyValidLinks) {
      return Option.empty
    }
    // check if the elements can all be distributed in tiles
    // basically this check to see if there are always neighboring
    // pe, mem and ce
    val tilesExist = processingElements.forall(pe => {
      memoryElements
        .find(mem => model.hasConnection(mem, pe) || model.hasConnection(pe, mem))
        .map(mem =>
          communicationElements.exists(ce =>
            (model.hasConnection(ce, pe) || model.hasConnection(pe, ce)) &&
              (model.hasConnection(mem, ce) || model.hasConnection(ce, mem))
          )
        )
        .getOrElse(false)
    })
    if (!tilesExist) {
      return Option.empty
    }
    // now tile elements via sorting of the processing elements
    val tiledMemories = memoryElements.sortBy(mem => {
      processingElements
        .filter(pe => model.hasConnection(pe, mem) || model.hasConnection(mem, pe))
        .map(pe => processingElements.indexOf(pe))
        .minOption
        .getOrElse(-1)
    })
    // we separate the comms in NI and routers
    val tileableCommElems = communicationElements.filter(ce => {
      processingElements.exists(pe => model.hasConnection(pe, ce) || model.hasConnection(ce, pe))
    })
    // and do the same as done for the memories
    val tiledCommElems = tileableCommElems.sortBy(ce => {
      processingElements
        .filter(pe => model.hasConnection(pe, ce) || model.hasConnection(ce, pe))
        .map(pe => processingElements.indexOf(pe))
        .minOption
        .getOrElse(-1)
    })
    val routers = communicationElements.filterNot(ce => tileableCommElems.contains(ce))
    // and also the subset of only communication elements
    var interconnectTopologySrcs = Buffer[String]()
    var interconnectTopologyDsts = Buffer[String]()
    communicationElements.foreach(ce => {
      model
        .outgoingEdgesOf(ce.getViewedVertex())
        .forEach(e => {
          val dst = model.getEdgeTarget(e)
          GenericCommunicationModule
            .safeCast(dst)
            .ifPresent(dstce => {
              interconnectTopologySrcs += ce.getIdentifier()
              interconnectTopologyDsts += dstce.getIdentifier()
            })
        })
    })
    val processorsProvisions = processingElements.map(pe => {
      // we do it mutable for simplicity...
      // the performance hit should not be a concern now, for super big instances, this can be reviewed
      var mutMap = mutable.Map[String, Map[String, Rational]]()
      InstrumentedProcessingModule
        .safeCast(pe)
        .map(ipe => {
          ipe
            .getModalInstructionsPerCycle()
            .entrySet()
            .forEach(e => {
              mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> Rational(v)).toMap
            })
        })
      mutMap.toMap
    })
    Option(
      TiledMultiCore(
        processingElements.map(_.getIdentifier()),
        memoryElements.map(_.getIdentifier()),
        tiledCommElems.map(_.getIdentifier()),
        routers.map(_.getIdentifier()),
        interconnectTopologySrcs.toArray,
        interconnectTopologyDsts.toArray,
        processorsProvisions,
        processingElements.map(_.getOperatingFrequencyInHertz().toLong),
        tiledMemories.map(_.getSpaceInBits()),
        communicationElements.map(
          InstrumentedCommunicationModule.safeCast(_).map(_.getMaxConcurrentFlits()).orElse(1)
        ),
        communicationElements.map(
          InstrumentedCommunicationModule
            .safeCast(_)
            .map(ce =>
              ce.getFlitSizeInBits() * ce.getMaxCyclesPerFlit() / ce.getOperatingFrequencyInHertz()
            )
            .orElse(1)
        ),
        preComputedPaths = Array.empty
      )
    )
  }
}
