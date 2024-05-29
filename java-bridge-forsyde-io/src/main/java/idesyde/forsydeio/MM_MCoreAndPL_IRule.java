package idesyde.forsydeio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import forsyde.io.core.VertexViewer;
import idesyde.common.MemoryMappableMulticoreWithPL;
import idesyde.core.*;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths;
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths;
import org.jgrapht.graph.AsSubgraph;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.hierarchy.platform.hardware.LogicProgrammableModule;
import forsyde.io.lib.hierarchy.platform.hardware.DigitalModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericCommunicationModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericMemoryModule;
import forsyde.io.lib.hierarchy.platform.hardware.GenericProcessingModule;
import forsyde.io.lib.hierarchy.platform.hardware.LogicProgrammableModuleViewer;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

@AutoRegister(ForSyDeIOModule.class)
class MemoryMappableMulticoreWithPL_IRule implements IdentificationRule {

    private record Pair<A, B>(A fst, B snd) {
    };

    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels,
            Set<? extends DecisionModel> decisionModels) {
        var identified = new HashSet<MemoryMappableMulticoreWithPL>();
        var errors = new HashSet<String>();
        var model = new SystemGraph();
        for (var dm : designModels) {
            ForSyDeIODesignModel.tryFrom(dm).map(ForSyDeIODesignModel::systemGraph).ifPresent(model::mergeInPlace);
        }
        var processingElements = new HashSet<GenericProcessingModule>();
        var memoryElements = new HashSet<GenericMemoryModule>();
        var communicationElements = new HashSet<GenericCommunicationModule>();
        var plElements = new HashSet<LogicProgrammableModule>(); // ! TODO don't use the viewer
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
                    ForSyDeHierarchy.LogicProgrammableModule
                            .tryView(model, v)
                            .ifPresent(p -> plElements.add(p));
                });
        var platformElements = new HashSet<DigitalModule>(processingElements);
        platformElements.addAll(memoryElements);
        platformElements.addAll(communicationElements);
        platformElements.addAll(plElements);
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
        var portAwareTopology = new DefaultDirectedGraph<Pair<String, String>, DefaultEdge>(DefaultEdge.class);
        for (var v : topology.vertexSet()) {
            portAwareTopology.addVertex(new Pair<>(v.getIdentifier(), null));
            for (var p : v.getPorts()) {
                portAwareTopology.addVertex(new Pair<>(v.getIdentifier(), p));
            }
            ForSyDeHierarchy.CommunicationModulePortSpecification.tryView(model, v)
                    .ifPresentOrElse(portedCommModule -> {
                        portedCommModule.portConnections().forEach((src, dsts) -> {
                            for (var dst : dsts) {
                                portAwareTopology.addEdge(new Pair<>(v.getIdentifier(), src),
                                        new Pair<>(v.getIdentifier(), dst));
                                portAwareTopology.addEdge(new Pair<>(v.getIdentifier(), dst),
                                        new Pair<>(v.getIdentifier(), src));
                            }
                        });
                    }, () -> {
                        for (var p : v.getPorts()) {
                            portAwareTopology.addEdge(new Pair<>(v.getIdentifier(), null),
                                    new Pair<>(v.getIdentifier(), p));
                            portAwareTopology.addEdge(new Pair<>(v.getIdentifier(), p),
                                    new Pair<>(v.getIdentifier(), null));
                        }
                    });
        }
        for (var e : topology.edgeSet()) {
            if (e.hasTrait(ForSyDeHierarchy.EdgeTraits.PhysicalConnection)) {
                e.getSourcePort().ifPresentOrElse(src -> {
                    e.getTargetPort().ifPresentOrElse(dst -> {
                        portAwareTopology.addEdge(new Pair<>(e.getSource(), src), new Pair<>(e.getTarget(), dst));
                    }, () -> errors.add("MemoryMappableMulticoreWithPL_IRule: %s to %s is not vertex-to-vertex or port-to-port."
                            .formatted(e.getSource(), e.getTarget())));
                }, () -> {
                    if (e.getTargetPort().isEmpty()) {
                        portAwareTopology.addEdge(new Pair<>(e.getSource(), null), new Pair<>(e.getTarget(), null));
                    } else {
                        errors.add("MemoryMappableMulticoreWithPL_IRule: %s to %s is not vertex-to-vertex or port-to-port."
                                .formatted(e.getSource(), e.getTarget()));
                    }
                });
            }
        }
        var connecivityInspector = new ConnectivityInspector<>(portAwareTopology);
        var shortestPaths = new FloydWarshallShortestPaths<>(portAwareTopology);
        for (var pe : processingElements) {
            if (memoryElements.stream()
                    .noneMatch(me -> connecivityInspector.pathExists(new Pair<>(pe.getIdentifier(), null),
                            new Pair<>(me.getIdentifier(), null)))) {
                errors.add("MemoryMappableMulticoreWithPL_IRule: %s does not reach any memory element".formatted(pe.getIdentifier()));
            }
        }

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
            errors.add("MemoryMappableMulticoreWithPL_IRule: no processing elements");
        }
        if (memoryElements.size() <= 0) {
            errors.add("MemoryMappableMulticoreWithPL_IRule: no memory elements");
        }
        if (plElements.size() <= 0) {
            errors.add("MemoryMappableMulticoreWithPL_IRule: no logic programmable elements");
        }
        if (!processingOnlyValidLinks || !memoryOnlyValidLinks) {
            errors.add("MemoryMappableMulticoreWithPL_IRule: processing or memory have invalid links");
        }
        if (errors.isEmpty()) {
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
            var totalAvailablePLAreas = plElements.stream()
                    .collect(Collectors.toMap(
                            VertexViewer::getIdentifier,
                            LogicProgrammableModule::availableLogicArea));
            var computedPlatformPaths = platformElements.stream().collect(
                    Collectors.toMap(
                            VertexViewer::getIdentifier,
                            src -> platformElements.stream()
                                    .filter(dst -> src != dst)
                                    .flatMap(dst -> {
                                        var path = shortestPaths.getPath(
                                                new Pair<>(src.getIdentifier(), null),
                                                new Pair<>(dst.getIdentifier(), null));
                                        if (path != null) {
                                            var filteredPath = new ArrayList<String>();
                                            filteredPath.add(src.getIdentifier());
                                            for (int i = 0; i <= path.getLength(); i++) {
                                                var len = filteredPath.size();
                                                if (len == 0 || !filteredPath.get(len - 1)
                                                        .equals(path.getVertexList().get(i).fst())) {
                                                    filteredPath.add(path.getVertexList().get(i).fst());
                                                }
                                            }
                                            return Stream.of(new Pair<>(dst, filteredPath));
                                        } else {
                                            return Stream.empty();
                                        }
                                    })
                                    .collect(Collectors
                                            .toMap(
                                                    e -> e.fst().getIdentifier(),
                                                    e -> e.snd()
                                                            .subList(1, e.snd()
                                                                    .size()
                                                                    - 1)))));
            identified.add(
                    new MemoryMappableMulticoreWithPL(
                            processingElements.stream()
                                    .map(x -> x.getIdentifier())
                                    .collect(Collectors.toSet()),
                            plElements.stream()
                                    .map(x -> x.getIdentifier())
                                    .collect(Collectors.toSet()),
                            totalAvailablePLAreas,
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
                            computedPlatformPaths));
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
