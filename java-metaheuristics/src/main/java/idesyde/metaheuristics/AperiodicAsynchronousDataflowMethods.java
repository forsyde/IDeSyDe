package idesyde.metaheuristics;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;

interface AperiodicAsynchronousDataflowMethods {

    default double[] recomputeMaximumCycles(
            final List<Set<Integer>> follows,
            final int[] mapping,
            final int[] ordering,
            final double[] jobWeights,
            final double[][] edgeWeigths) {
        BiFunction<Integer, Integer, Boolean> mustSuceed = (i, j) -> {
            return follows.get(i).contains(j)
                    || (mapping[i] == mapping[j] && ordering[i] + 1 == ordering[j]);
        };
        BiFunction<Integer, Integer, Boolean> mustCycle = (i, j) -> {
            return mapping[i] == mapping[j] ? ordering[j] == 0 && ordering[i] > 0 : false;
        };
        var maxCycles = new double[jobWeights.length];
        var maximumCycleVector = new double[jobWeights.length];
        var dfsStack = new ArrayDeque<Integer>(jobWeights.length);
        var visited = new boolean[jobWeights.length];
        var previous = new int[jobWeights.length];
        for (int src = 0; src < jobWeights.length; src++) {
            dfsStack.clear();
            Arrays.fill(visited, false);
            Arrays.fill(previous, -1);
            Arrays.fill(maximumCycleVector, Double.NEGATIVE_INFINITY);
            dfsStack.push(src);
            while (!dfsStack.isEmpty()) {
                var i = dfsStack.pop();
                if (!visited[i]) {
                    visited[i] = true;
                    for (int j = 0; j < jobWeights.length; j++) {
                        if (mustSuceed.apply(i, j) || mustCycle.apply(i, j)) {
                            if (j == src) { // found a cycle
                                maximumCycleVector[i] = jobWeights[i]
                                        + edgeWeigths[i][j];
                                var k = i;
                                // go backwards until the src
                                while (k != src) {
                                    var kprev = previous[k];
                                    maximumCycleVector[kprev] = Math.max(
                                            maximumCycleVector[kprev],
                                            jobWeights[kprev]
                                                    + edgeWeigths[kprev][k]
                                                    + maximumCycleVector[k]);
                                    k = kprev;
                                }
                            } else if (visited[j]
                                    && maximumCycleVector[j] > Integer.MIN_VALUE) { // found
                                                                                    // a
                                                                                    // previous
                                                                                    // cycle
                                var k = j;
                                // go backwards until the src
                                while (k != src) {
                                    var kprev = previous[k];
                                    maximumCycleVector[kprev] = Math.max(
                                            maximumCycleVector[kprev],
                                            jobWeights[kprev]
                                                    + edgeWeigths[kprev][k]
                                                    + maximumCycleVector[k]);
                                    k = kprev;
                                }
                            } else if (!visited[j]) {
                                dfsStack.push(j);
                                previous[j] = i;
                            }

                        }
                    }
                }
            }
            maxCycles[src] = maximumCycleVector[src] > Double.NEGATIVE_INFINITY ? maximumCycleVector[src]
                    : jobWeights[src];
        }
        for (int i = 0; i < jobWeights.length; i++) {
            for (int j = 0; j < jobWeights.length; j++) {
                if (mustSuceed.apply(i, j) || mustSuceed.apply(j, i)) {
                    maxCycles[i] = Math.max(maxCycles[i], maxCycles[j]);
                    maxCycles[j] = Math.max(maxCycles[i], maxCycles[j]);
                }
            }
        }

        // for (
        // group <- m.sdfApplications.sdfDisjointComponents; a1 <- group; a2 <- group;
        // if a1 != a2;
        // a1i = m.sdfApplications.actorsIdentifiers.indexOf(a1);
        // a2i = m.sdfApplications.actorsIdentifiers.indexOf(a2);
        // qa1 = m.sdfApplications.sdfRepetitionVectors(a1i);
        // qa2 = m.sdfApplications.sdfRepetitionVectors(a2i)
        // ) {
        // ths(a1i) = Math.min(ths(a1i), ths(a2i) * qa1 / qa2)
        // ths(a2i) = Math.min(ths(a1i) * qa2 / qa1, ths(a2i))
        // }
        return maxCycles;
    }

    default boolean mappingIsFeasible(
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
        return decisionModel.processesToRuntimeScheduling().entrySet().stream().allMatch(e -> {
            var process = e.getKey();
            var hostSched = e.getValue();
            var hostPe = decisionModel.partitionedMemMappableMulticore().runtimes().runtimeHost()
                    .get(hostSched);
            return decisionModel.instrumentedComputationTimes().worstExecutionTimes().get(process)
                    .containsKey(hostPe);
        }) && decisionModel.partitionedMemMappableMulticore().hardware().storageSizes().entrySet().stream()
                .allMatch(memEntry -> {
                    var taskUsage = decisionModel.processesToMemoryMapping().entrySet().stream()
                            .filter(taskEntry -> taskEntry.getValue()
                                    .equals(memEntry.getKey()))
                            .mapToLong(taskEntry -> decisionModel
                                    .instrumentedMemoryRequirements()
                                    .memoryRequirements().get(taskEntry.getKey())
                                    .get(memEntry.getKey()))
                            .sum();
                    var channelUsage = decisionModel.bufferToMemoryMappings().entrySet().stream()
                            .filter(taskEntry -> taskEntry.getValue()
                                    .equals(memEntry.getKey()))
                            .mapToLong(taskEntry -> decisionModel
                                    .instrumentedMemoryRequirements()
                                    .memoryRequirements().get(taskEntry.getKey())
                                    .get(memEntry.getKey()))
                            .sum();
                    return taskUsage + channelUsage <= memEntry.getValue();
                });
    }

}
