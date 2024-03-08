package idesyde.forsydeio;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.hierarchy.behavior.moc.sdf.SDFActor;
import forsyde.io.lib.hierarchy.behavior.moc.sdf.SDFChannel;
import idesyde.common.SDFApplication;
import idesyde.core.*;

@AutoRegister(ForSyDeIOModule.class)
class ForSyDeIOSDFToCommon implements IdentificationRule {

    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels, Set<? extends DecisionModel> u) {
        var model = new SystemGraph();
        var msgs = new HashSet<String>();
        var identified = new HashSet<DecisionModel>();
        for (var dm : designModels) {
            if (dm instanceof ForSyDeIODesignModel m) {
                model.mergeInPlace(m.systemGraph());
            }
        }
        var sdfActors = new ArrayList<SDFActor>();
        var selfConcurrentActors = new HashSet<String>();
        var allSdfChannels = new ArrayList<SDFChannel>();
        for (var v : model.vertexSet()) {
            ForSyDeHierarchy.SDFActor.tryView(model, v).ifPresent(sdfActors::add);
            ForSyDeHierarchy.SDFChannel.tryView(model, v).ifPresent(allSdfChannels::add);
        }
        var properSdfChannels = new ArrayList<SDFChannel>();
        for (var c : allSdfChannels) {
            var outgoing = c.producer().map(sdfActors::contains).orElse(false);
            var incoming = c.consumer().map(sdfActors::contains).orElse(false);
            if (outgoing && incoming) {
                properSdfChannels.add(c);
                if (c.producer().equals(c.consumer())) {
                    selfConcurrentActors.add(c.producer().get().getIdentifier());
                }
            } else if (!outgoing && !incoming) {
                msgs.add("ForSyDeIOSDFToCommon: channel %s has no producer and consumer.".formatted(c.getIdentifier()));
            }
        }
        if (sdfActors.isEmpty()) {
            msgs.add("ForSyDeIOSDFToCommon: no actors found.");
        } else {
            var topologySrcs = new ArrayList<String>();
            var topologyDsts = new ArrayList<String>();
            var topologyProduction = new ArrayList<Integer>();
            var topologyConsumption = new ArrayList<Integer>();
            var topologyChannelNames = new ArrayList<Set<String>>();
            var topologyInitialTokens = new ArrayList<Integer>();
            var topologyTokenSizeInBits = new ArrayList<Long>();
            for (var c : properSdfChannels) {
                c.producer().ifPresent(src -> {
                    c.consumer().ifPresent(dst -> {
                        var idx = IntStream.range(0, topologySrcs.size())
                                .filter(i -> topologySrcs.get(i).equals(src.getIdentifier())
                                        && topologyDsts.get(i).equals(dst.getIdentifier()))
                                .findFirst().orElse(-1);
                        var prod = model.getAllEdges(src, c).stream()
                                .mapToInt(e -> e.getSourcePort().map(sp -> src.production().get(sp)).orElse(0)).sum();
                        var cons = model.getAllEdges(c.getViewedVertex(), dst.getViewedVertex()).stream()
                                .mapToInt(e -> e.getTargetPort().map(tp -> dst.consumption().get(tp)).orElse(0)).sum();
                        var size = ForSyDeHierarchy.BufferLike.tryView(c).map(b -> b.elementSizeInBits().longValue())
                                .orElse(0L);
                        if (idx > -1) {
                            topologyProduction.set(idx, topologyProduction.get(idx) + prod);
                            topologyConsumption.set(idx, topologyConsumption.get(idx) + cons);
                            topologyChannelNames.get(idx).add(c.getIdentifier());
                            topologyInitialTokens.set(idx,
                                    topologyInitialTokens.get(idx) + c.numInitialTokens().intValue());
                            topologyTokenSizeInBits.set(idx, topologyTokenSizeInBits.get(idx) + size);
                        } else {
                            topologySrcs.add(src.getIdentifier());
                            topologyDsts.add(dst.getIdentifier());
                            topologyProduction.add(prod);
                            topologyConsumption.add(cons);
                            var names = new HashSet<String>();
                            names.add(c.getIdentifier());
                            topologyChannelNames.add(names);
                            topologyInitialTokens.add(c.numInitialTokens().intValue());
                            topologyTokenSizeInBits.add(size);
                        }
                    });
                });
            }
            identified.add(
                    new SDFApplication(
                            new HashMap<String, Double>(),
                            sdfActors.stream().map(SDFActor::getIdentifier).collect(Collectors.toSet()),
                            new HashMap<String, Map<String, Double>>(),
                            properSdfChannels.stream().map(SDFChannel::getIdentifier).collect(Collectors.toSet()),
                            properSdfChannels.stream().collect(Collectors.toMap(
                                    c -> c.getIdentifier(),
                                    c -> c.tokenDataType().flatMap(ForSyDeHierarchy.InstrumentedDataType::tryView)
                                            .flatMap(d -> d.maxSizeInBits().values().stream().max(Long::compare))
                                            .orElse(0L))),
                            selfConcurrentActors,
                            topologyChannelNames,
                            topologyConsumption,
                            topologyDsts,
                            topologyInitialTokens,
                            topologyProduction,
                            topologySrcs,
                            topologyTokenSizeInBits));
        }
        return new IdentificationResult(identified, msgs);
    }

    // val processComputationalNeeds = sdfActors.map(fromSDFActorToNeeds(model,
    // _)).toVector
    // (
    // if (sdfActors.size > 0) {
    // Set(
    // SDFApplicationWithFunctions(
    // sdfActors.map(_.getIdentifier()).toVector,
    // sdfChannels.map(_.getIdentifier()).toVector,
    // topologySrcs.toVector,
    // topologyDsts.toVector,
    // topologyEdgeValue.toVector,
    // processSizes,
    // processComputationalNeeds,
    // sdfChannels.map(_.numInitialTokens().toInt).toVector,
    // sdfChannels
    // .map(
    // ForSyDeHierarchy.BufferLike
    // .tryView(_)
    // .map(_.elementSizeInBits().toLong)
    // .orElse(0L)
    // )
    // .toVector,
    // sdfActors.map(a => -1.0).toVector
    // )
    // )
    // } else Set(),
    // errors.toSet
    // )
    // }
    // // val modelOpt = models
    // // .filter(_.isInstanceOf[ForSyDeDesignModel])
    // // .map(_.asInstanceOf[ForSyDeDesignModel])
    // // .map(_.systemGraph)
    // // .reduceOption(_.merge(_))
    // // modelOpt
    // // .map(model => {

    // // val model = modelOpt.get
    // // })
    // // .getOrElse((Set(), Set()))
    // }

    private Map<String, Map<String, Long>> fromSDFActorToNeeds(SystemGraph model, SDFActor actor) {
        var mutMap = new HashMap<String, HashMap<String, Long>>();
        actor.combFunctions().forEach(func -> {
            ForSyDeHierarchy.InstrumentedBehaviour.tryView(func).ifPresent(ifunc -> {
                ifunc.computationalRequirements().forEach((k, v) -> {
                    if (mutMap.containsKey(k)) {
                        v.forEach((innerK, innerV) -> {
                            mutMap.get(k).put(innerK, mutMap.get(k).getOrDefault(innerK, 0L) + innerV);
                        });
                    } else {
                        mutMap.put(k, new HashMap<String, Long>());
                        v.forEach((innerK, innerV) -> {
                            mutMap.get(k).put(innerK, innerV);
                        });
                    }
                });
            });
        });
        // check also the actor, just in case, this might be best
        // in case the functions don't exist, but the actors is instrumented
        // anyway
        ForSyDeHierarchy.InstrumentedBehaviour.tryView(actor).ifPresent(iactor -> {
            iactor.computationalRequirements().forEach((k, v) -> {
                if (mutMap.containsKey(k)) {
                    v.forEach((innerK, innerV) -> {
                        mutMap.get(k).put(innerK, mutMap.get(k).getOrDefault(innerK, 0L) + innerV);
                    });
                } else {
                    mutMap.put(k, new HashMap<String, Long>());
                    v.forEach((innerK, innerV) -> {
                        mutMap.get(k).put(innerK, innerV);
                    });
                }
            });
        });
        return mutMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()));
    }

}
