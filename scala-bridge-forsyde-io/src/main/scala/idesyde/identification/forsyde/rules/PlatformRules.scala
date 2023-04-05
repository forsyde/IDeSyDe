package idesyde.identification.forsyde.rules

import scala.jdk.CollectionConverters._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.platform.TiledMultiCore
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import idesyde.forsydeio.ForSyDeDesignModel
import forsyde.io.java.typed.viewers.platform.DigitalModule
import scala.collection.mutable.Buffer
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import scala.collection.mutable
import spire.math.Rational
import idesyde.identification.common.models.platform.PartitionedCoresWithRuntimes
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.platform.runtime.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import idesyde.identification.common.models.platform.SharedMemoryMultiCore
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.connectivity.ConnectivityInspector

trait PlatformRules {

  def identPartitionedCoresWithRuntimes(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[PartitionedCoresWithRuntimes] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var processingElements = Buffer[GenericProcessingModule]()
      var runtimeElements    = Buffer[AbstractScheduler]()
      model.vertexSet.stream
        .forEach(v => {
          GenericProcessingModule
            .safeCast(v)
            .ifPresent(p => processingElements :+= p)
          AbstractScheduler
            .safeCast(v)
            .ifPresent(p => runtimeElements :+= p)
        })
      lazy val allocated = processingElements.map(pe => {
        runtimeElements.find(s => {
          model.hasConnection(s, pe) || model.hasConnection(pe, s)
        })
      })
      if (
        processingElements.length > 0 && processingElements.size <= runtimeElements.size && !allocated
          .exists(_.isEmpty)
      ) {
        Set(
          PartitionedCoresWithRuntimes(
            processingElements.map(_.getIdentifier()).toVector,
            allocated.map(_.get.getIdentifier()).toVector,
            allocated
              .map(_.get)
              .map(v => !FixedPriorityScheduler.conforms(v) && !StaticCyclicScheduler.conforms(v))
              .toVector,
            allocated
              .map(_.get)
              .map(v => FixedPriorityScheduler.conforms(v) && !StaticCyclicScheduler.conforms(v))
              .toVector,
            allocated
              .map(_.get)
              .map(v => !FixedPriorityScheduler.conforms(v) && StaticCyclicScheduler.conforms(v))
              .toVector
          )
        )
      } else Set()
    }
  }

  def identTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[TiledMultiCore] = {
    val modelOpt = models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
    if (modelOpt.isEmpty) {
      return Set.empty
    }
    val model                 = modelOpt.get
    var processingElements    = Buffer.empty[GenericProcessingModule]
    var memoryElements        = Buffer.empty[GenericMemoryModule]
    var communicationElements = Buffer.empty[GenericCommunicationModule]
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
    val topology = AsSubgraph(
      model,
      (processingElements ++ memoryElements ++ communicationElements)
        .map(_.getViewedVertex())
        .toSet
        .asJava
    )
    // check if pes and mes connect only to CE etc
    lazy val processingOnlyValidLinks = processingElements.forall(pe => {
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
    // do the same for MEs
    lazy val memoryOnlyValidLinks = memoryElements.forall(me => {
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
    // check if the elements can all be distributed in tiles
    // basically this check to see if there are always neighboring
    // pe, mem and ce
    lazy val tilesExist = processingElements.forall(pe => {
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
    // now tile elements via sorting of the processing elements
    lazy val tiledMemories = memoryElements.sortBy(mem => {
      processingElements
        .filter(pe => model.hasConnection(pe, mem) || model.hasConnection(mem, pe))
        .map(pe => processingElements.indexOf(pe))
        .minOption
        .getOrElse(-1)
    })
    // we separate the comms in NI and routers
    lazy val tileableCommElems = communicationElements.filter(ce => {
      processingElements.exists(pe => model.hasConnection(pe, ce) || model.hasConnection(ce, pe))
    })
    // and do the same as done for the memories
    lazy val tiledCommElems = tileableCommElems.sortBy(ce => {
      processingElements
        .filter(pe => model.hasConnection(pe, ce) || model.hasConnection(ce, pe))
        .map(pe => processingElements.indexOf(pe))
        .minOption
        .getOrElse(-1)
    })
    lazy val routers = communicationElements.filterNot(ce => tileableCommElems.contains(ce))
    // and also the subset of only communication elements
    lazy val processorsProvisions = processingElements.map(pe => {
      // we do it mutable for simplicity...
      // the performance hit should not be a concern now, for super big instances, this can be reviewed
      var mutMap = mutable.Map[String, Map[String, Double]]()
      InstrumentedProcessingModule
        .safeCast(pe)
        .map(ipe => {
          ipe
            .getModalInstructionsPerCycle()
            .entrySet()
            .forEach(e => {
              mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> v.toDouble).toMap
            })
        })
      mutMap.toMap
    })
    if (
      processingElements.length > 0 &&
      processingElements.size <= memoryElements.size &&
      processingElements.size <= communicationElements.size &&
      processingOnlyValidLinks &&
      memoryOnlyValidLinks &&
      tilesExist
    ) {
      var interconnectTopologySrcs = Buffer[String]()
      var interconnectTopologyDsts = Buffer[String]()
      topology
        .edgeSet()
        .forEach(e => {
          interconnectTopologySrcs += topology.getEdgeSource(e).getIdentifier()
          interconnectTopologyDsts += topology.getEdgeTarget(e).getIdentifier()
        })
      Set(
        TiledMultiCore(
          processingElements.map(_.getIdentifier()).toVector,
          memoryElements.map(_.getIdentifier()).toVector,
          tiledCommElems.map(_.getIdentifier()).toVector,
          routers.map(_.getIdentifier()).toVector,
          interconnectTopologySrcs.toVector,
          interconnectTopologyDsts.toVector,
          processorsProvisions.toVector,
          processingElements.map(_.getOperatingFrequencyInHertz().toLong).toVector,
          tiledMemories.map(_.getSpaceInBits().toLong).toVector,
          communicationElements
            .map(
              InstrumentedCommunicationModule
                .safeCast(_)
                .map(_.getMaxConcurrentFlits().toInt)
                .orElse(1)
            )
            .toVector,
          communicationElements
            .map(
              InstrumentedCommunicationModule
                .safeCast(_)
                .map(ce =>
                  ce.getFlitSizeInBits() * ce.getMaxCyclesPerFlit() * ce
                    .getOperatingFrequencyInHertz()
                )
                .map(_.toDouble)
                .orElse(0.0)
            )
            .toVector,
          preComputedPaths = Map.empty
        )
      )
    } else Set()
  }

  def identSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[SharedMemoryMultiCore] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var processingElements    = Buffer.empty[GenericProcessingModule]
      var memoryElements        = Buffer.empty[GenericMemoryModule]
      var communicationElements = Buffer.empty[GenericCommunicationModule]
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
      // build the topology graph with just the known elements
      lazy val topology = AsSubgraph(
        model,
        (processingElements ++ memoryElements ++ communicationElements)
          .map(_.getViewedVertex())
          .toSet
          .asJava
      )
      // check if pes and mes connect only to CE etc
      lazy val processingOnlyValidLinks = processingElements.forall(pe => {
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
      // do the same for MEs
      lazy val memoryOnlyValidLinks = memoryElements.forall(me => {
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
      // check if all processors are connected to at least one memory element
      lazy val connecivityInspector = ConnectivityInspector(topology)
      lazy val pesConnected = processingElements.forall(pe =>
        memoryElements.exists(me =>
          connecivityInspector.pathExists(pe.getViewedVertex(), me.getViewedVertex())
        )
      )
      // basically this check to see if there are always neighboring
      // pe, mem and ce
      // and also the subset of only communication elements
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
      if (
        processingElements.length > 0 &&
        memoryElements.length > 0 &&
        processingOnlyValidLinks &&
        memoryOnlyValidLinks &&
        pesConnected
      ) {
        var interconnectTopologySrcs = Buffer[String]()
        var interconnectTopologyDsts = Buffer[String]()
        topology
          .edgeSet()
          .forEach(e => {
            interconnectTopologySrcs += topology.getEdgeSource(e).getIdentifier()
            interconnectTopologyDsts += topology.getEdgeTarget(e).getIdentifier()
          })
        Set(
          SharedMemoryMultiCore(
            processingElements.map(_.getIdentifier()).toVector,
            memoryElements.map(_.getIdentifier()).toVector,
            communicationElements.map(_.getIdentifier()).toVector,
            interconnectTopologySrcs.toVector,
            interconnectTopologyDsts.toVector,
            processingElements.map(_.getOperatingFrequencyInHertz().toLong).toVector,
            processorsProvisions.toVector,
            memoryElements.map(_.getSpaceInBits().toLong).toVector,
            communicationElements
              .map(
                InstrumentedCommunicationModule
                  .safeCast(_)
                  .map(_.getMaxConcurrentFlits().toInt)
                  .orElse(1)
              )
              .toVector,
            communicationElements
              .map(
                InstrumentedCommunicationModule
                  .safeCast(_)
                  .map(ce =>
                    Rational(
                      ce.getFlitSizeInBits() * ce.getMaxCyclesPerFlit() * ce
                        .getOperatingFrequencyInHertz()
                    )
                  )
                  .orElse(Rational.zero)
              )
              .toVector,
            preComputedPaths = Map.empty
          )
        )
      } else Set()
    }
  }
}
