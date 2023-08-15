package idesyde.identification.forsyde.rules

import scala.jdk.CollectionConverters._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.common.TiledMultiCoreWithFunctions
import idesyde.forsydeio.ForSyDeDesignModel
import scala.collection.mutable.Buffer
import scala.collection.mutable
import spire.math.Rational
import idesyde.common.PartitionedCoresWithRuntimes
import idesyde.common.SharedMemoryMultiCore
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.connectivity.ConnectivityInspector
import forsyde.io.lib.platform.hardware.GenericProcessingModule
import forsyde.io.lib.platform.runtime.AbstractRuntime
import forsyde.io.lib.ForSyDeHierarchy
import forsyde.io.lib.platform.hardware.GenericMemoryModule
import forsyde.io.lib.platform.hardware.GenericCommunicationModule

trait PlatformRules {

  def identPartitionedCoresWithRuntimes(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PartitionedCoresWithRuntimes], Set[String]) = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var errors             = mutable.Set[String]()
      var processingElements = Buffer[GenericProcessingModule]()
      var runtimeElements    = Buffer[AbstractRuntime]()
      model.vertexSet.stream
        .forEach(v => {
          ForSyDeHierarchy.GenericProcessingModule
            .tryView(model, v)
            .ifPresent(p => processingElements :+= p)
          ForSyDeHierarchy.AbstractRuntime
            .tryView(model, v)
            .ifPresent(p => runtimeElements :+= p)
        })
      lazy val allocated = processingElements.map(pe => {
        runtimeElements.find(s => {
          model.hasConnection(s, pe) || model.hasConnection(pe, s)
        })
      })
      if (processingElements.length <= 0) {
        errors += "identPartitionedCoresWithRuntimes: no processing elements"
      }
      if (processingElements.size > runtimeElements.size) {
        errors += "identPartitionedCoresWithRuntimes: more processing elements than runtimes"
      }
      if (allocated.exists(_.isEmpty)) {
        errors += "identPartitionedCoresWithRuntimes: not all runtimes are mapped/allocated"
      }
      val m =
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
                .map(v =>
                  !ForSyDeHierarchy.FixedPriorityScheduledRuntime
                    .tryView(v)
                    .isPresent() && !ForSyDeHierarchy.SuperLoopRuntime
                    .tryView(v)
                    .isPresent()
                )
                .toVector,
              allocated
                .map(_.get)
                .map(v =>
                  ForSyDeHierarchy.FixedPriorityScheduledRuntime
                    .tryView(v)
                    .isPresent() && !ForSyDeHierarchy.SuperLoopRuntime
                    .tryView(v)
                    .isPresent()
                )
                .toVector,
              allocated
                .map(_.get)
                .map(v =>
                  !ForSyDeHierarchy.FixedPriorityScheduledRuntime
                    .tryView(v)
                    .isPresent() && ForSyDeHierarchy.SuperLoopRuntime
                    .tryView(v)
                    .isPresent()
                )
                .toVector
            )
          )
        } else Set()
      (m, errors.toSet)
    }
  }

  def identTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[TiledMultiCoreWithFunctions], Set[String]) = {
    val modelOpt = models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
    modelOpt
      .map(model => {
        var errors                = mutable.Set[String]()
        val model                 = modelOpt.get
        var processingElements    = Buffer.empty[GenericProcessingModule]
        var memoryElements        = Buffer.empty[GenericMemoryModule]
        var communicationElements = Buffer.empty[GenericCommunicationModule]
        model.vertexSet.stream
          .filter(v => ForSyDeHierarchy.DigitalModule.tryView(model, v).isPresent())
          .forEach(v => {
            ForSyDeHierarchy.GenericProcessingModule
              .tryView(model, v)
              .ifPresent(p => processingElements :+= p)
            ForSyDeHierarchy.GenericMemoryModule
              .tryView(model, v)
              .ifPresent(p => memoryElements :+= p)
            ForSyDeHierarchy.GenericCommunicationModule
              .tryView(model, v)
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
            .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
            .allMatch(v =>
              ForSyDeHierarchy.GenericCommunicationModule
                .tryView(model, v)
                .isPresent() || ForSyDeHierarchy.GenericMemoryModule.tryView(model, v).isPresent()
            )
          &&
          topology
            .incomingEdgesOf(pe.getViewedVertex)
            .stream
            .map(topology.getEdgeSource(_))
            .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
            .allMatch(v =>
              ForSyDeHierarchy.GenericCommunicationModule
                .tryView(model, v)
                .isPresent() || ForSyDeHierarchy.GenericMemoryModule.tryView(model, v).isPresent()
            )
        })
        // do the same for MEs
        lazy val memoryOnlyValidLinks = memoryElements.forall(me => {
          topology
            .outgoingEdgesOf(me.getViewedVertex)
            .stream
            .map(topology.getEdgeTarget(_))
            .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
            .allMatch(v =>
              ForSyDeHierarchy.GenericCommunicationModule
                .tryView(model, v)
                .isPresent() || ForSyDeHierarchy.GenericProcessingModule
                .tryView(model, v)
                .isPresent()
            )
          &&
          topology
            .incomingEdgesOf(me.getViewedVertex)
            .stream
            .map(topology.getEdgeSource(_))
            .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
            .allMatch(v =>
              ForSyDeHierarchy.GenericCommunicationModule
                .tryView(model, v)
                .isPresent() || ForSyDeHierarchy.GenericProcessingModule
                .tryView(model, v)
                .isPresent()
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
          processingElements.exists(pe =>
            model.hasConnection(pe, ce) || model.hasConnection(ce, pe)
          )
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
          ForSyDeHierarchy.InstrumentedProcessingModule
            .tryView(pe)
            .map(ipe => {
              ipe
                .modalInstructionsPerCycle()
                .entrySet()
                .forEach(e => {
                  mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> v.toDouble).toMap
                })
            })
          mutMap.toMap
        })
        if (processingElements.length <= 0) {
          errors += "identTiledMultiCore: no processing elements"
        }
        if (processingElements.size > memoryElements.size) {
          errors += "identTiledMultiCore: less memories than processors"
        }
        if (processingElements.size > communicationElements.size) {
          errors += "identTiledMultiCore: less communication elements than processors"
        }
        if (
          !processingOnlyValidLinks ||
          !memoryOnlyValidLinks
        ) { errors += "identTiledMultiCore: processing or memory have invalid links for tiling" }
        if (!tilesExist) { errors += "identTiledMultiCore: not all tiles exist" }
        val m =
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
              TiledMultiCoreWithFunctions(
                processingElements.map(_.getIdentifier()).toVector,
                memoryElements.map(_.getIdentifier()).toVector,
                tiledCommElems.map(_.getIdentifier()).toVector,
                routers.map(_.getIdentifier()).toVector,
                interconnectTopologySrcs.toVector,
                interconnectTopologyDsts.toVector,
                processorsProvisions.toVector,
                processingElements.map(_.operatingFrequencyInHertz().toLong).toVector,
                tiledMemories.map(_.spaceInBits().toLong).toVector,
                communicationElements
                  .map(
                    ForSyDeHierarchy.InstrumentedCommunicationModule
                      .tryView(_)
                      .map(_.maxConcurrentFlits().toInt)
                      .orElse(1)
                  )
                  .toVector,
                communicationElements
                  .map(
                    ForSyDeHierarchy.InstrumentedCommunicationModule
                      .tryView(_)
                      .map(ce =>
                        ce.flitSizeInBits() * ce.maxCyclesPerFlit() * ce
                          .operatingFrequencyInHertz()
                      )
                      .map(_.toDouble)
                      .orElse(0.0)
                  )
                  .toVector,
                preComputedPaths = Map.empty
              )
            )
          } else Set()
        (m, errors.toSet)
      })
      .getOrElse((Set(), Set()))
  }

  def identSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SharedMemoryMultiCore], Set[String]) = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var errors                = mutable.Set[String]()
      var processingElements    = Buffer.empty[GenericProcessingModule]
      var memoryElements        = Buffer.empty[GenericMemoryModule]
      var communicationElements = Buffer.empty[GenericCommunicationModule]
      model.vertexSet.stream
        .forEach(v => {
          ForSyDeHierarchy.GenericProcessingModule
            .tryView(model, v)
            .ifPresent(p => processingElements :+= p)
          ForSyDeHierarchy.GenericMemoryModule
            .tryView(model, v)
            .ifPresent(p => memoryElements :+= p)
          ForSyDeHierarchy.GenericCommunicationModule
            .tryView(model, v)
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
          .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
          .allMatch(v =>
            ForSyDeHierarchy.GenericCommunicationModule
              .tryView(model, v)
              .isPresent() || ForSyDeHierarchy.GenericMemoryModule.tryView(model, v).isPresent()
          )
        &&
        topology
          .incomingEdgesOf(pe.getViewedVertex)
          .stream
          .map(topology.getEdgeSource(_))
          .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
          .allMatch(v =>
            ForSyDeHierarchy.GenericCommunicationModule
              .tryView(model, v)
              .isPresent() || ForSyDeHierarchy.GenericMemoryModule
              .tryView(model, v)
              .isPresent()
          )
      })
      // do the same for MEs
      lazy val memoryOnlyValidLinks = memoryElements.forall(me => {
        topology
          .outgoingEdgesOf(me.getViewedVertex)
          .stream
          .map(topology.getEdgeTarget(_))
          .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
          .allMatch(v =>
            ForSyDeHierarchy.GenericCommunicationModule
              .tryView(model, v)
              .isPresent() || ForSyDeHierarchy.GenericProcessingModule
              .tryView(model, v)
              .isPresent()
          )
        &&
        topology
          .incomingEdgesOf(me.getViewedVertex)
          .stream
          .map(topology.getEdgeSource(_))
          .filter(ForSyDeHierarchy.DigitalModule.tryView(model, _).isPresent())
          .allMatch(v =>
            ForSyDeHierarchy.GenericCommunicationModule
              .tryView(model, v)
              .isPresent() || ForSyDeHierarchy.GenericProcessingModule
              .tryView(model, v)
              .isPresent()
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
        var mutMap = mutable.Map[String, Map[String, Double]]()
        ForSyDeHierarchy.InstrumentedProcessingModule
          .tryView(pe)
          .map(ipe => {
            ipe
              .modalInstructionsPerCycle()
              .entrySet()
              .forEach(e => {
                mutMap(e.getKey()) = e.getValue().asScala.map((k, v) => k -> v.toDouble).toMap
              })
          })
        mutMap.toMap
      })
      if (processingElements.length <= 0) {
        errors += "identSharedMemoryMultiCore: no processing elements"
      }
      if (memoryElements.length <= 0) { errors += "identSharedMemoryMultiCore: no memory elements" }
      if (!processingOnlyValidLinks || !memoryOnlyValidLinks) {
        errors += "identSharedMemoryMultiCore: processing or memory have invalid links"
      }
      if (!pesConnected) {
        errors += "identSharedMemoryMultiCore: not all processing elements reach a memory element"
      }
      val m =
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
              processingElements.map(_.operatingFrequencyInHertz().toLong).toVector,
              processorsProvisions.toVector,
              memoryElements.map(_.spaceInBits().toLong).toVector,
              communicationElements
                .map(
                  ForSyDeHierarchy.InstrumentedCommunicationModule
                    .tryView(_)
                    .map(_.maxConcurrentFlits().toInt)
                    .orElse(1)
                )
                .toVector,
              communicationElements
                .map(
                  ForSyDeHierarchy.InstrumentedCommunicationModule
                    .tryView(_)
                    .map(ce =>
                      ce.flitSizeInBits().toDouble * ce.maxCyclesPerFlit().toDouble * ce
                        .operatingFrequencyInHertz()
                        .toDouble
                    )
                    .orElse(0.0)
                )
                .toVector,
              preComputedPaths = Map.empty
            )
          )
        } else Set()
      (m, errors.toSet)
    }
  }
}
