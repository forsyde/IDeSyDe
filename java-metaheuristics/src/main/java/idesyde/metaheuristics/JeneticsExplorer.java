package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.AutoRegister;
import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import idesyde.core.ExplorationBidding;
import idesyde.core.ExplorationEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@AutoRegister(MetaHeuristicsExplorationModule.class)
public class JeneticsExplorer implements Explorer, CanExploreAADPMMMWithJenetics, CanExploreAADPTMWithJenetics {

    @Override
    public ExplorationBidding bid(DecisionModel decisionModel) {
        var objs = new HashSet<String>();
        switch (decisionModel.category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                objs.add("nUsedPEs");
                DecisionModel
                        .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)
                        .ifPresent(aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore -> {
                            for (var app : aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
                                    .aperiodicAsynchronousDataflows()) {
                                for (var actor : app.processes()) {
                                    if (!app.processMinimumThroughput().containsKey(actor)) {
                                        objs.add("invThroughput(%s)".formatted(actor));
                                    }
                                }
                            }
                        });
                return new ExplorationBidding(true, false, 1.1, objs, Map.of("time-to-first", 10.0));
            case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore":
                objs.add("nUsedPEs");
                DecisionModel
                        .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)
                        .ifPresent(m -> {
                            for (var app : m
                                    .aperiodicAsynchronousDataflows()) {
                                for (var actor : app.processes()) {
                                    if (!app.processMinimumThroughput().containsKey(actor)) {
                                        objs.add("invThroughput(%s)".formatted(actor));
                                    }
                                }
                            }
                        });
                return new ExplorationBidding(true, false, 1.1, objs, Map.of("time-to-first", 10.0));
            default:
                return Explorer.super.bid(decisionModel);
        }
    }

    @Override
    public Stream<? extends ExplorationEvent> explore(DecisionModel decisionModel,
            Set<ExplorationSolution> previousSolutions, Configuration configuration) {
        switch (decisionModel.category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                return DecisionModel
                        .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)
                        .map(m -> exploreAADPMMM(m, previousSolutions, configuration)).orElse(Stream.empty())
                        .dropWhile(sol -> previousSolutions.contains(sol) ||
                                previousSolutions.stream().anyMatch(prev -> prev.dominates(sol)))
                        .map(m -> new ExplorationEvent(Optional.of(m), false));
                        // .peek(s -> totalSolutions.add(s));
            case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore":
                return DecisionModel
                        .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)
                        .map(m -> exploreAADPTM(m, previousSolutions, configuration)).orElse(Stream.empty())
                        .dropWhile(sol -> previousSolutions.contains(sol) ||
                                previousSolutions.stream().anyMatch(prev -> prev.dominates(sol)))
                        .map(m -> new ExplorationEvent(Optional.of(m), false));
                        // .peek(s -> totalSolutions.add(s));
            default:
                return Stream.empty();
        }
    }

}
