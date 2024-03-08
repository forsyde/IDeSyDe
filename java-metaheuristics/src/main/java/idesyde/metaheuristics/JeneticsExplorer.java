package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.AutoRegister;
import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import idesyde.core.ExplorationBidding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

@AutoRegister(MetaHeuristicsExplorationModule.class)
public class JeneticsExplorer implements Explorer, CanExploreAADPMMMWithJenetics, CanExploreAADPTMWithJenetics {

    @Override
    public ExplorationBidding bid(Set<Explorer> explorers, DecisionModel decisionModel) {
        return DecisionModel
                .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)
                .map(aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore -> {
                    var objs = new HashSet<String>();
                    objs.add("nUsedPEs");
                    for (var app : aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
                            .aperiodicAsynchronousDataflows()) {
                        for (var actor : app.processes()) {
                            if (!app.processMinimumThroughput().containsKey(actor)) {
                                objs.add("invThroughput(%s)".formatted(actor));
                            }
                        }
                    }
                    return new ExplorationBidding(true, false, 1.1, objs, Map.of("time-to-first", 10.0));
                })
                .or(() -> DecisionModel
                        .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)
                        .map(m -> {
                            var objs = new HashSet<String>();
                            objs.add("nUsedPEs");
                            for (var app : m
                                    .aperiodicAsynchronousDataflows()) {
                                for (var actor : app.processes()) {
                                    if (!app.processMinimumThroughput().containsKey(actor)) {
                                        objs.add("invThroughput(%s)".formatted(actor));
                                    }
                                }
                            }
                            return new ExplorationBidding(true, false, 1.1, objs, Map.of("time-to-first", 10.0));
                        }))
                .orElse(Explorer.super.bid(explorers, decisionModel));
        // if (decisionModel instanceof
        // AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
        // aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
        // var objs = new HashSet<String>();
        // objs.add("nUsedPEs");
        // for (var app :
        // aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
        // .aperiodicAsynchronousDataflows()) {
        // for (var actor : app.processes()) {
        // if (!app.processMinimumThroughput().containsKey(actor)) {
        // objs.add("invThroughput(%s)".formatted(actor));
        // }
        // }
        // }
        // return new ExplorationBidding(true, false, 1.1, objs, Map.of("time-to-first",
        // 10.0));
        // } else if (decisionModel instanceof
        // AperiodicAsynchronousDataflowToPartitionedTiledMulticore
        // aperiodicAsynchronousDataflowToPartitionedTiledMulticore) {
        // var objs = new HashSet<String>();
        // objs.add("nUsedPEs");
        // for (var app : aperiodicAsynchronousDataflowToPartitionedTiledMulticore
        // .aperiodicAsynchronousDataflows()) {
        // for (var actor : app.processes()) {
        // if (!app.processMinimumThroughput().containsKey(actor)) {
        // objs.add("invThroughput(%s)".formatted(actor));
        // }
        // }
        // }
        // return new ExplorationBidding(true, false, 1.1, objs, Map.of("time-to-first",
        // 10.0));
        // }
        // return Explorer.super.bid(explorers, decisionModel);
    }

    @Override
    public Stream<? extends ExplorationSolution> explore(DecisionModel decisionModel,
            Set<ExplorationSolution> previousSolutions, Configuration configuration) {
        var foundSolutionObjectives = new CopyOnWriteArraySet<Map<String, Double>>();
        var explorationStream = DecisionModel
                .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)
                .map(m -> exploreAADPMMM(m, previousSolutions, configuration))
                .or(() -> DecisionModel
                        .cast(decisionModel, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)
                        .map(m -> exploreAADPTM(m, previousSolutions, configuration)))
                .orElse(Stream.empty());
        return explorationStream
                .filter(sol -> !previousSolutions.contains(sol) &&
                        !foundSolutionObjectives.contains(sol.objectives()))
                .peek(s -> foundSolutionObjectives.add(s.objectives()));
    }

    // @Override
    // public List<Job>
    // memoizedJobs(AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
    // decisionModel) {
    // if (!_memoizedJobs.containsKey(decisionModel)) {
    // _memoizedJobs.put(decisionModel,
    // decisionModel.aperiodicAsynchronousDataflows().stream()
    // .flatMap(app ->
    // app.jobsOfProcesses().stream()).collect(Collectors.toList()));
    // }
    // return _memoizedJobs.get(decisionModel);
    // }

    // @Override
    // public List<Set<Job>> memoizeFollows(
    // AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
    // decisionModel) {
    // if (!_memoizedFollows.containsKey(decisionModel)) {
    // var jobs = memoizedJobs(decisionModel);
    // var follows = jobs.stream()
    // .map(src -> jobs.stream().filter(dst ->
    // decisionModel.aperiodicAsynchronousDataflows()
    // .stream()
    // .anyMatch(app -> {
    // var srcI = app.jobGraphSrcName().indexOf(src.process());
    // var dstI = app.jobGraphDstName().indexOf(dst.process());
    // return srcI > -1 && dstI > -1 && srcI == dstI &&
    // app.jobGraphSrcInstance().get(srcI) == src.instance() &&
    // app.jobGraphDstInstance().get(dstI) == dst.instance();
    // }))
    // .collect(Collectors.toSet()))
    // .collect(Collectors.toList());
    // _memoizedFollows.put(decisionModel, follows);
    // }
    // return _memoizedFollows.get(decisionModel);
    // }
}
