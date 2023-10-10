package idesyde.forsydeio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.hierarchy.behavior.moc.sy.SYDelay;
import forsyde.io.lib.hierarchy.behavior.moc.sy.SYMap;
import forsyde.io.lib.hierarchy.behavior.moc.sy.SYSignal;
import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationResult;
import idesyde.core.IdentificationRule;

class ForSyDeIOSYNetworkToAADataflowIRule implements IdentificationRule {

    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels,
            Set<? extends DecisionModel> decisionModels) {
        var identified = new HashSet<AperiodicAsynchronousDataflow>();
        var msgs = new HashSet<String>();
        var model = new SystemGraph();
        for (var dm : designModels) {
            if (dm instanceof ForSyDeIODesignModel m) {
                model.mergeInPlace(m.systemGraph());
            }
        }
        var onlySyComponents = new AsSubgraph<>(
                model,
                model
                        .vertexSet()
                        .stream()
                        .filter(v -> ForSyDeHierarchy.SYProcess.tryView(model, v).isPresent()
                                || ForSyDeHierarchy.SYSignal
                                        .tryView(model, v)
                                        .isPresent())
                        .collect(Collectors.toSet()));
        var inspector = new ConnectivityInspector<>(onlySyComponents);
        var wcc = inspector.connectedSets();
        if (wcc.isEmpty())
            msgs.add("identAperiodicDataflowFromSY: could not find any SY connected components.");
        wcc
                .stream()
                .forEach(subModel -> {
                    var syMaps = new HashSet<SYMap>();
                    var sySignals = new HashSet<SYSignal>();
                    var syDelays = new HashSet<SYDelay>();
                    subModel
                            .forEach(v -> {
                                ForSyDeHierarchy.SYMap.tryView(model, v).ifPresent(syMaps::add);
                                ForSyDeHierarchy.SYSignal.tryView(model, v).ifPresent(sySignals::add);
                                ForSyDeHierarchy.SYDelay.tryView(model, v).ifPresent(syDelays::add);
                            });
                    var msgSizes = new HashMap<String, Long>();
                    for (var sig : sySignals) {
                        msgSizes.put(sig.getIdentifier(), ForSyDeHierarchy.RegisterArrayLike
                                .tryView(sig)
                                .map(s -> s.elementSizeInBits())
                                .orElse(0L));
                    }
                    var mapsAndDelays = Stream.concat(syMaps.stream(), syDelays.stream()).collect(Collectors.toSet());
                    var jobGraphName = syMaps.stream().map(x -> x.getIdentifier()).collect(Collectors.toList());
                    var jobGraphSrc = new ArrayList<String>();
                    var jobGraphDst = new ArrayList<String>();
                    var jobGraphStrong = new ArrayList<Boolean>();
                    sySignals.forEach(sig -> {
                        sig
                                .consumers()
                                .forEach(dst -> {
                                    sig.producer().ifPresent(src -> {
                                        // System.out.println("%s: %s >- %s".formatted(sig.getIdentifier(),
                                        // src.getIdentifier(), dst.getIdentifier()));
                                        if (ForSyDeHierarchy.SYMap
                                                .tryView(src)
                                                .isPresent() && ForSyDeHierarchy.SYMap.tryView(dst).isPresent()) {
                                            jobGraphSrc.add(src.getIdentifier());
                                            jobGraphDst.add(dst.getIdentifier());
                                            jobGraphStrong.add(true);
                                        } else if (ForSyDeHierarchy.SYDelay.tryView(src).isPresent()) {
                                            jobGraphSrc.add(dst.getIdentifier());
                                            jobGraphDst.add(src.getIdentifier());
                                            jobGraphStrong.add(true);
                                        }
                                    });
                                });
                    });
                    identified.add(new AperiodicAsynchronousDataflow(
                            msgSizes,
                            sySignals.stream().map(s -> s.getIdentifier()).collect(Collectors.toSet()),
                            jobGraphName,
                            jobGraphName.stream().map(x -> 1L).collect(Collectors.toList()),
                            jobGraphDst,
                            jobGraphDst.stream().map(x -> 1L).collect(Collectors.toList()),
                            jobGraphStrong,
                            jobGraphSrc,
                            jobGraphSrc.stream().map(x -> 1L).collect(Collectors.toList()),
                            mapsAndDelays.stream()
                                    .collect(Collectors.toMap(
                                            proc -> proc.getIdentifier(),
                                            proc -> sySignals.stream().filter(sig -> sig.consumers().contains(proc))
                                                    .collect(Collectors.toMap(
                                                            sig -> sig.getIdentifier(),
                                                            sig -> msgSizes.get(sig.getIdentifier()))))),
                            Map.of(), // min throughput
                            Map.of(), // max latency paths
                            mapsAndDelays.stream()
                                    .collect(Collectors.toMap(
                                            proc -> proc.getIdentifier(),
                                            proc -> sySignals.stream().filter(
                                                    sig -> sig.producer()
                                                            .map(x -> x.getIdentifier().equals(proc.getIdentifier()))
                                                            .orElse(false))
                                                    .collect(Collectors.toMap(
                                                            sig -> sig.getIdentifier(),
                                                            sig -> msgSizes.get(sig.getIdentifier()))))),
                            mapsAndDelays.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet())));
                });
        return new IdentificationResult(identified, msgs);
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
