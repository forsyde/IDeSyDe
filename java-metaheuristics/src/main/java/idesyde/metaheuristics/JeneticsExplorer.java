package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflow.Job;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import idesyde.core.headers.ExplorationBidding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JeneticsExplorer implements Explorer, CanExploreAADPMMMWithJenetics, CanExploreAADPTMWithJenetics {

    private Map<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore, List<AperiodicAsynchronousDataflow.Job>> _memoizedJobs = new HashMap<>();
    private Map<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore, List<Set<AperiodicAsynchronousDataflow.Job>>> _memoizedFollows = new HashMap<>();

    @Override
    public ExplorationBidding bid(DecisionModel decisionModel) {
        if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
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
            return new ExplorationBidding(uniqueIdentifier(), true, false, 1.3, objs, Map.of());
        } else if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedTiledMulticore aperiodicAsynchronousDataflowToPartitionedTiledMulticore) {
            var objs = new HashSet<String>();
            objs.add("nUsedPEs");
            for (var app : aperiodicAsynchronousDataflowToPartitionedTiledMulticore
                    .aperiodicAsynchronousDataflows()) {
                for (var actor : app.processes()) {
                    if (!app.processMinimumThroughput().containsKey(actor)) {
                        objs.add("invThroughput(%s)".formatted(actor));
                    }
                }
            }
            return new ExplorationBidding(uniqueIdentifier(), true, false, 1.3, objs, Map.of());
        }
        return Explorer.super.bid(decisionModel);
    }

    @Override
    public Stream<? extends ExplorationSolution> explore(DecisionModel decisionModel,
            Set<ExplorationSolution> previousSolutions, Configuration configuration) {
        if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
            return exploreAADPMMM(aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore, previousSolutions,
                    configuration);
        } else if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedTiledMulticore aperiodicAsynchronousDataflowToPartitionedTiledMulticore) {
            return explore(aperiodicAsynchronousDataflowToPartitionedTiledMulticore, previousSolutions,
                    configuration);
        }
        return Explorer.super.explore(decisionModel, previousSolutions, configuration);
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
