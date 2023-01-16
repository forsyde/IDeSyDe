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
import idesyde.identification.common.models.platform.PartitionedCoresWithRuntimes
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import forsyde.io.java.core.ForSyDeSystemGraph
import breeze.linalg.all
import forsyde.io.java.typed.viewers.platform.runtime.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import idesyde.identification.common.models.platform.SharedMemoryMultiCore
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.connectivity.ConnectivityInspector

object PlatformRules {

  def identPartitionedCoresWithRuntimes(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[PartitionedCoresWithRuntimes] = {
    val modelOpt = models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
    if (modelOpt.isEmpty) {
      return Option.empty
    }
    val model: ForSyDeSystemGraph = modelOpt.get
    var processingElements        = Buffer[GenericProcessingModule]()
    var runtimeElements           = Buffer[AbstractScheduler]()
    model.vertexSet.stream
      .forEach(v => {
        GenericProcessingModule
          .safeCast(v)
          .ifPresent(p => processingElements :+= p)
        AbstractScheduler
          .safeCast(v)
          .ifPresent(p => runtimeElements :+= p)
      })
    if (
      processingElements.length <= 0 &&
      processingElements.size >= runtimeElements.size
    ) {
      return Option.empty
    }
    val allocated = processingElements.map(pe => {
      runtimeElements.find(s => {
        model.hasConnection(s, pe) || model.hasConnection(pe, s)
      })
    })
    if (allocated.exists(_.isEmpty)) {
      return Option.empty
    }
    Option(
      PartitionedCoresWithRuntimes(
        processingElements.map(_.getIdentifier()).toArray,
        allocated.map(_.get.getIdentifier()).toArray,
        allocated
          .map(_.get)
          .map(v => !FixedPriorityScheduler.conforms(v) && !StaticCyclicScheduler.conforms(v))
          .toArray,
        allocated
          .map(_.get)
          .map(v => FixedPriorityScheduler.conforms(v) && !StaticCyclicScheduler.conforms(v))
          .toArray,
        allocated
          .map(_.get)
          .map(v => !FixedPriorityScheduler.conforms(v) && StaticCyclicScheduler.conforms(v))
          .toArray
      )
    )
  }

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
    val topology = AsSubgraph(model, (processingElements ++ memoryElements ++ communicationElements).map(_.getViewedVertex()).toSet.asJava)
    // check if pes and mes connect only to CE etc
    val processingOnlyValidLinks = processingElements.forall(pe => {
      topology
        .outgoingEdgesOf(pe.getViewedVertex)
        .stream
        .map(topology.getEdgeTarget(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
      &&
      topology
        .incomingEdgesOf(pe.getViewedVertex)
        .stream
        .map(topology.getEdgeSource(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
    })
    if (!processingOnlyValidLinks) {
      return Option.empty
    }
    // do the same for MEs
    val memoryOnlyValidLinks = memoryElements.forall(me => {
      topology
        .outgoingEdgesOf(me.getViewedVertex)
        .stream
        .map(topology.getEdgeTarget(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v =>
          GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
        )
      &&
      topology
        .incomingEdgesOf(me.getViewedVertex)
        .stream
        .map(topology.getEdgeSource(_))
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
    topology.edgeSet().forEach(e => {
      interconnectTopologySrcs += topology.getEdgeSource(e).getIdentifier()
      interconnectTopologyDsts += topology.getEdgeTarget(e).getIdentifier()
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
              Rational(ce.getFlitSizeInBits() * ce.getMaxCyclesPerFlit() * ce.getOperatingFrequencyInHertz())
            )
            .orElse(Rational.zero)
        ),
        preComputedPaths = Map.empty
      )
    )
  }

  def identSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[SharedMemoryMultiCore] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
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
        processingElements.length <= 0
      ) {
        return Option.empty
      }
      // build the topology graph with just the known elements
      val topology = AsSubgraph(model, (processingElements ++ memoryElements ++ communicationElements).map(_.getViewedVertex()).toSet.asJava)
      // check if pes and mes connect only to CE etc
      val processingOnlyValidLinks = processingElements.forall(pe => {
        topology
          .outgoingEdgesOf(pe.getViewedVertex)
          .stream
          .map(topology.getEdgeTarget(_))
          .filter(DigitalModule.conforms(_))
          .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
        &&
        topology
          .incomingEdgesOf(pe.getViewedVertex)
          .stream
          .map(topology.getEdgeSource(_))
          .filter(DigitalModule.conforms(_))
          .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
      })
      if (!processingOnlyValidLinks) {
        return Option.empty
      }
      // do the same for MEs
      val memoryOnlyValidLinks = memoryElements.forall(me => {
        topology
          .outgoingEdgesOf(me.getViewedVertex)
          .stream
          .map(topology.getEdgeTarget(_))
          .filter(DigitalModule.conforms(_))
          .allMatch(v =>
            GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
          )
        &&
        topology
          .incomingEdgesOf(me.getViewedVertex)
          .stream
          .map(topology.getEdgeSource(_))
          .filter(DigitalModule.conforms(_))
          .allMatch(v =>
            GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
          )
      })
      if (!memoryOnlyValidLinks) {
        return Option.empty
      }
      // check if all processors are connected to at least one memory element
      val connecivityInspector = ConnectivityInspector(topology)
      val pesConnected = processingElements.forall(pe => 
        memoryElements.exists(me => connecivityInspector.pathExists(pe.getViewedVertex(), me.getViewedVertex())))
      if (!pesConnected) return Option.empty
      // basically this check to see if there are always neighboring
      // pe, mem and ce
      // and also the subset of only communication elements
      var interconnectTopologySrcs = Buffer[String]()
      var interconnectTopologyDsts = Buffer[String]()
      topology.edgeSet().forEach(e => {
        interconnectTopologySrcs += topology.getEdgeSource(e).getIdentifier()
        interconnectTopologyDsts += topology.getEdgeTarget(e).getIdentifier()
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
        SharedMemoryMultiCore(
          processingElements.map(_.getIdentifier()),
          memoryElements.map(_.getIdentifier()),
          communicationElements.map(_.getIdentifier()),
          interconnectTopologySrcs.toArray,
          interconnectTopologyDsts.toArray,
          processingElements.map(_.getOperatingFrequencyInHertz().toLong),
          processorsProvisions,
          memoryElements.map(_.getSpaceInBits()),
          communicationElements.map(
            InstrumentedCommunicationModule.safeCast(_).map(_.getMaxConcurrentFlits()).orElse(1)
          ),
          communicationElements.map(
            InstrumentedCommunicationModule
              .safeCast(_)
              .map(ce =>
                Rational(ce.getFlitSizeInBits() * ce.getMaxCyclesPerFlit() * ce.getOperatingFrequencyInHertz())
              )
              .orElse(Rational.zero)
          ),
          preComputedPaths = Map.empty
        )
      )
    }
  }
}
