package idesyde.forsydeio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.hierarchy.platform.hardware.DigitalModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericCommunicationModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericMemoryModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericProcessingModule;
import idesyde.common.MemoryMappableMultiCore;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationResult;
import idesyde.core.IdentificationRule;

class MemoryMappableMultiCoreIRule implements IdentificationRule {

        @Override
        public IdentificationResult apply(Set<? extends DesignModel> designModels,
                        Set<? extends DecisionModel> decisionModels) {
                var identified = new HashSet<MemoryMappableMultiCore>();
                var errors = new HashSet<String>();
                var model = new SystemGraph();
                for (var dm : designModels) {
                        if (dm instanceof ForSyDeIODesignModel m) {
                                model.mergeInPlace(m.systemGraph());
                        }
                }
                var processingElements = new HashSet<GenericProcessingModule>();
                var memoryElements = new HashSet<GenericMemoryModule>();
                var communicationElements = new HashSet<GenericCommunicationModule>();
                model.vertexSet().stream()
                                .forEach(v -> {
                                        ForSyDeHierarchy.GenericProcessingModule
                                                        .tryView(model, v)
                                                        .ifPresent(p -> processingElements.add(p));
                                        ForSyDeHierarchy.GenericMemoryModule
                                                        .tryView(model, v)
                                                        .ifPresent(p -> memoryElements.add(p));
                                        ForSyDeHierarchy.GenericCommunicationModule
                                                        .tryView(model, v)
                                                        .ifPresent(p -> communicationElements.add(p));
                                });
                var platformElements = new HashSet<DigitalModule>(processingElements);
                platformElements.addAll(memoryElements);
                platformElements.addAll(communicationElements);
                // build the topology graph with just the known elements
                var topology = new AsSubgraph<>(
                                model,
                                platformElements.stream().map(v -> v.getViewedVertex())
                                                .collect(Collectors.toSet()));
                // check if pes and mes connect only to CE etc
                var processingOnlyValidLinks = processingElements.stream().allMatch(pe -> topology
                                .outgoingEdgesOf(pe.getViewedVertex()).stream()
                                .map(topology::getEdgeTarget)
                                .filter(v -> ForSyDeHierarchy.DigitalModule.tryView(model, v)
                                                .isPresent())
                                .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                                                .tryView(model, v)
                                                .isPresent()
                                                || ForSyDeHierarchy.GenericMemoryModule
                                                                .tryView(model, v).isPresent())
                                &&
                                topology
                                                .incomingEdgesOf(pe.getViewedVertex()).stream()
                                                .map(topology::getEdgeSource)
                                                .filter(v -> ForSyDeHierarchy.DigitalModule
                                                                .tryView(model, v).isPresent())
                                                .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                                                                .tryView(model, v)
                                                                .isPresent()
                                                                || ForSyDeHierarchy.GenericMemoryModule
                                                                                .tryView(model, v)
                                                                                .isPresent()));
                // do the same for MEs
                var memoryOnlyValidLinks = memoryElements.stream().allMatch(me -> topology
                                .outgoingEdgesOf(me.getViewedVertex()).stream()
                                .map(topology::getEdgeTarget)
                                .filter(v -> ForSyDeHierarchy.DigitalModule.tryView(model, v)
                                                .isPresent())
                                .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                                                .tryView(model, v)
                                                .isPresent()
                                                || ForSyDeHierarchy.GenericProcessingModule
                                                                .tryView(model, v)
                                                                .isPresent())
                                &&
                                topology
                                                .incomingEdgesOf(me.getViewedVertex()).stream()
                                                .map(topology::getEdgeSource)
                                                .filter(v -> ForSyDeHierarchy.DigitalModule
                                                                .tryView(model, v).isPresent())
                                                .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                                                                .tryView(model, v)
                                                                .isPresent()
                                                                || ForSyDeHierarchy.GenericProcessingModule
                                                                                .tryView(model, v)
                                                                                .isPresent()));
                // check if all processors are connected to at least one memory element
                var connecivityInspector = new ConnectivityInspector<>(topology);
                var pesConnected = processingElements.stream().allMatch(pe -> memoryElements.stream()
                                .anyMatch(me -> connecivityInspector.pathExists(pe.getViewedVertex(),
                                                me.getViewedVertex())));
                // basically this check to see if there are always neighboring
                // pe, mem and ce
                // and also the subset of only communication elements
                var processorsProvisions = new HashMap<String, Map<String, Map<String, Double>>>();
                for (var pe : processingElements) {
                        ForSyDeHierarchy.InstrumentedProcessingModule
                                        .tryView(pe)
                                        .ifPresent(ipe -> processorsProvisions.put(pe.getIdentifier(),
                                                        ipe
                                                                        .modalInstructionsPerCycle()));
                }
                // var processorsProvisions = processingElements.map(pe -> {
                // // we do it mutable for simplicity...
                // // the performance hit should not be a concern now, for super big instances,
                // this can be reviewed
                // var mutMap = new HashMap<String, Map<String, Double>>();
                // return mutMap;
                // });
                if (processingElements.size() <= 0) {
                        errors.add("MemoryMappableMultiCoreIRule: no processing elements");
                }
                if (memoryElements.size() <= 0) {
                        errors.add("MemoryMappableMultiCoreIRule: no memory elements");
                }
                if (!processingOnlyValidLinks || !memoryOnlyValidLinks) {
                        errors.add("MemoryMappableMultiCoreIRule: processing or memory have invalid links");
                }
                if (!pesConnected) {
                        errors.add("MemoryMappableMultiCoreIRule: not all processing elements reach a memory element");
                }
                if (processingElements.size() > 0 &&
                                memoryElements.size() > 0 &&
                                processingOnlyValidLinks &&
                                memoryOnlyValidLinks &&
                                pesConnected) {
                        var interconnectTopologySrcs = new ArrayList<String>();
                        var interconnectTopologyDsts = new ArrayList<String>();
                        topology
                                        .edgeSet()
                                        .forEach(e -> {
                                                interconnectTopologySrcs.add(topology.getEdgeSource(e)
                                                                .getIdentifier());
                                                interconnectTopologyDsts.add(topology.getEdgeTarget(e)
                                                                .getIdentifier());
                                        });
                        var procFreqs = processingElements.stream().collect(Collectors.toMap(
                                        pe -> pe.getIdentifier(),
                                        pe -> pe.operatingFrequencyInHertz()));
                        var memSizes = memoryElements.stream()
                                        .collect(Collectors.toMap(
                                                        me -> me.getIdentifier(),
                                                        me -> me.spaceInBits()));
                        var maxConcurrentFlits = communicationElements.stream()
                                        .collect(Collectors.toMap(
                                                        ce -> ce.getIdentifier(),
                                                        ce -> ForSyDeHierarchy.InstrumentedCommunicationModule
                                                                        .tryView(ce)
                                                                        .map(ice -> ice.maxConcurrentFlits())
                                                                        .orElse(1)));
                        var bandwidths = communicationElements.stream()
                                        .collect(Collectors.toMap(
                                                        ce -> ce.getIdentifier(),
                                                        ce -> ForSyDeHierarchy.InstrumentedCommunicationModule
                                                                        .tryView(ce)
                                                                        .stream()
                                                                        .mapToDouble(ice -> (double) ice
                                                                                        .flitSizeInBits()
                                                                                        * (double) ice.operatingFrequencyInHertz()
                                                                                        / (double) ice.maxCyclesPerFlit())
                                                                        .max()
                                                                        .orElse(0.0)));
                        identified.add(
                                        new MemoryMappableMultiCore(
                                                        processingElements.stream()
                                                                        .map(x -> x.getIdentifier())
                                                                        .collect(Collectors.toSet()),
                                                        memoryElements.stream()
                                                                        .map(x -> x.getIdentifier())
                                                                        .collect(Collectors.toSet()),
                                                        communicationElements.stream()
                                                                        .map(x -> x.getIdentifier())
                                                                        .collect(Collectors.toSet()),
                                                        interconnectTopologySrcs,
                                                        interconnectTopologyDsts,
                                                        procFreqs,
                                                        processorsProvisions,
                                                        memSizes,
                                                        maxConcurrentFlits,
                                                        bandwidths,
                                                        Map.of()));
                }
                return new IdentificationResult(identified, errors);
        }

        @Override
        public boolean usesDecisionModels() {
                return false;
        }

        @Override
        public boolean usesDesignModels() {
                return true;
        }

}
