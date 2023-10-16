package idesyde.forsydeio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.graph.AsSubgraph;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.hierarchy.platform.hardware.DigitalModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericCommunicationModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericMemoryModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericProcessingModule;
import idesyde.common.MemoryMappableMultiCore;
import idesyde.common.TiledMultiCore;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationResult;
import idesyde.core.IdentificationRule;

class TiledMultiCoreIRule implements IdentificationRule {

    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels,
            Set<? extends DecisionModel> decisionModels) {
        var identified = new HashSet<TiledMultiCore>();
        var errors = new HashSet<String>();
        var model = new SystemGraph();
        for (var dm : designModels) {
            if (dm instanceof ForSyDeIODesignModel m) {
                model.mergeInPlace(m.systemGraph());
            }
        }
        var processingElements = new ArrayList<GenericProcessingModule>();
        var memoryElements = new ArrayList<GenericMemoryModule>();
        var communicationElements = new ArrayList<GenericCommunicationModule>();
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
        var platformElements = new HashSet<DigitalModule>();
        platformElements.addAll(processingElements);
        platformElements.addAll(memoryElements);
        platformElements.addAll(communicationElements);
        // build the topology graph with just the known elements
        var topology = new AsSubgraph<>(
                model,
                platformElements.stream().map(v -> v.getViewedVertex())
                        .collect(Collectors.toSet()));
        // check if pes and mes connect only to CE etc
        var processingOnlyValidLinks = processingElements.stream().allMatch(pe -> topology
                .outgoingEdgesOf(pe.getViewedVertex())
                .stream()
                .map(topology::getEdgeTarget)
                .filter(v -> ForSyDeHierarchy.DigitalModule.tryView(model, v).isPresent())
                .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                        .tryView(model, v)
                        .isPresent() || ForSyDeHierarchy.GenericMemoryModule.tryView(model, v).isPresent())
                &&
                topology
                        .incomingEdgesOf(pe.getViewedVertex())
                        .stream()
                        .map(topology::getEdgeSource)
                        .filter(v -> ForSyDeHierarchy.DigitalModule.tryView(model, v).isPresent())
                        .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                                .tryView(model, v)
                                .isPresent() || ForSyDeHierarchy.GenericMemoryModule.tryView(model, v).isPresent()));
        // do the same for MEs
        var memoryOnlyValidLinks = memoryElements.stream().allMatch(me -> topology
                .outgoingEdgesOf(me.getViewedVertex())
                .stream()
                .map(topology::getEdgeTarget)
                .filter(v -> ForSyDeHierarchy.DigitalModule.tryView(model, v).isPresent())
                .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                        .tryView(model, v)
                        .isPresent()
                        || ForSyDeHierarchy.GenericProcessingModule
                                .tryView(model, v)
                                .isPresent())
                &&
                topology
                        .incomingEdgesOf(me.getViewedVertex())
                        .stream()
                        .map(topology::getEdgeSource)
                        .filter(v -> ForSyDeHierarchy.DigitalModule.tryView(model, v).isPresent())
                        .allMatch(v -> ForSyDeHierarchy.GenericCommunicationModule
                                .tryView(model, v)
                                .isPresent()
                                || ForSyDeHierarchy.GenericProcessingModule
                                        .tryView(model, v)
                                        .isPresent()));
        // check if the elements can all be distributed in tiles
        // basically this check to see if there are always neighboring
        // pe, mem and ce
        var tilesExist = processingElements.stream().allMatch(pe -> memoryElements
                .stream()
                .filter(mem -> model.hasConnection(mem, pe) || model.hasConnection(pe, mem))
                .filter(mem -> communicationElements.stream()
                        .anyMatch(ce -> (model.hasConnection(ce, pe) || model.hasConnection(pe, ce)) &&
                                (model.hasConnection(mem, ce) || model.hasConnection(ce, mem))))
                .findAny().isPresent());
        // // now tile elements via sorting of the processing elements
        var tiledMemories = memoryElements.stream().sorted((memA, memB) -> Integer.compare(processingElements.stream()
                .filter(pe -> model.hasConnection(pe, memA) || model.hasConnection(memA, pe))
                .mapToInt(pe -> processingElements.indexOf(pe))
                .min().orElse(-1),
                processingElements.stream()
                        .filter(pe -> model.hasConnection(pe, memB) || model.hasConnection(memB, pe))
                        .mapToInt(pe -> processingElements.indexOf(pe))
                        .min().orElse(-1)))
                .collect(Collectors.toList());
        // we separate the comms in NI and routers
        var tileableCommElems = communicationElements.stream()
                .filter(ce -> processingElements.stream().anyMatch(pe -> model.hasConnection(pe, ce) ||
                        model.hasConnection(ce, pe)));
        // and do the same as done for the memories
        var tiledCommElems = tileableCommElems.sorted((ceA, ceB) -> Integer.compare(processingElements.stream()
                .filter(pe -> model.hasConnection(pe, ceA) || model.hasConnection(ceA, pe))
                .mapToInt(pe -> processingElements.indexOf(pe)).min()
                .orElse(-1),
                processingElements.stream()
                        .filter(pe -> model.hasConnection(pe, ceB) || model.hasConnection(ceB, pe))
                        .mapToInt(pe -> processingElements.indexOf(pe)).min()
                        .orElse(-1)))
                .collect(Collectors.toList());
        var routers = communicationElements.stream().filter(ce -> !tiledCommElems.contains(ce))
                .collect(Collectors.toList());
        // and also the subset of only communication elements
        var processorsProvisions = new HashMap<String, Map<String, Map<String, Double>>>();
        for (var pe : processingElements) {
            ForSyDeHierarchy.InstrumentedProcessingModule
                    .tryView(pe)
                    .ifPresent(ipe -> processorsProvisions.put(pe.getIdentifier(),
                            ipe
                                    .modalInstructionsPerCycle()));
        }
        if (processingElements.size() <= 0) {
            errors.add("identTiledMultiCore: no processing elements");
        }
        if (processingElements.size() > memoryElements.size()) {
            errors.add("identTiledMultiCore: less memories than processors");
        }
        if (processingElements.size() > communicationElements.size()) {
            errors.add("identTiledMultiCore: less communication elements than processors");
        }
        if (!processingOnlyValidLinks ||
                !memoryOnlyValidLinks) {
            errors.add("identTiledMultiCore: processing or memory have invalid links for tiling");
        }
        if (!tilesExist) {
            errors.add("identTiledMultiCore: not all tiles exist");
        }
        if (processingElements.size() > 0 &&
                processingElements.size() <= memoryElements.size() &&
                processingElements.size() <= communicationElements.size() &&
                processingOnlyValidLinks &&
                memoryOnlyValidLinks &&
                tilesExist) {
            var interconnectTopologySrcs = new ArrayList<String>();
            var interconnectTopologyDsts = new ArrayList<String>();
            topology
                    .edgeSet()
                    .forEach(e -> {
                        interconnectTopologySrcs.add(topology.getEdgeSource(e).getIdentifier());
                        interconnectTopologyDsts.add(topology.getEdgeTarget(e).getIdentifier());
                    });
            identified.add(
                    new TiledMultiCore(
                            communicationElements.stream()
                                    .collect(Collectors.toMap(
                                            ce -> ce.getIdentifier(),
                                            ce -> ForSyDeHierarchy.InstrumentedCommunicationModule.tryView(ce)
                                                    .map(ie -> ie.operatingFrequencyInHertz()
                                                            * ie.flitSizeInBits() / ie.maxCyclesPerFlit())
                                                    .orElse(0L).doubleValue())),
                            communicationElements.stream()
                                    .collect(Collectors.toMap(
                                            ce -> ce.getIdentifier(),
                                            ce -> ForSyDeHierarchy.InstrumentedCommunicationModule
                                                    .tryView(ce)
                                                    .map(ie -> ie.maxConcurrentFlits())
                                                    .orElse(1))),
                            interconnectTopologySrcs,
                            interconnectTopologyDsts,
                            memoryElements.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet()),
                            tiledCommElems.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet()),
                            Map.of(),
                            processingElements.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet()),
                            processingElements.stream()
                                    .collect(Collectors.toMap(
                                            x -> x.getIdentifier(),
                                            x -> x.operatingFrequencyInHertz())),
                            processorsProvisions,
                            routers.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet()),
                            tiledMemories.stream()
                                    .collect(Collectors.toMap(
                                            x -> x.getIdentifier(),
                                            x -> x.spaceInBits()))));
        }
        return new IdentificationResult(identified, errors);
    }

}
