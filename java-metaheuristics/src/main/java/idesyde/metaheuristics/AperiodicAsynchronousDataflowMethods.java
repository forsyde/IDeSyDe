package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;

import java.util.Arrays;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.*;

interface AperiodicAsynchronousDataflowMethods {

    default double[] recomputeMaximumCycles(
            final Graph<Integer, DefaultEdge> follows,
            final int[] mapping,
            final int[] ordering,
            final double[] jobWeights,
            final double[][] edgeWeigths) {
        var mappingGraph = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
        for (int i = 0; i < mapping.length; i++) {
            for (int j = 0; j < mapping.length; j++) {
                if (mapping[i] == mapping[j] && ordering[i] + 1 == ordering[j]) {
                    mappingGraph.addVertex(i);
                    mappingGraph.addVertex(j);
                    mappingGraph.addEdge(i, j);
                } else if (mapping[i] == mapping[j] && ordering[j] == 0 && ordering[i] > 0) {
                    mappingGraph.addVertex(i);
                    mappingGraph.addVertex(j);
                    mappingGraph.addEdge(i, j);
                }
            }
        }
        var mergedGraph = new AsGraphUnion<>(follows, mappingGraph);
        var maxCycles = new double[jobWeights.length];
        System.arraycopy(jobWeights, 0, maxCycles, 0, jobWeights.length);
        var sccAlgorithm = new KosarajuStrongConnectivityInspector<>(mergedGraph);
        sccAlgorithm.stronglyConnectedSets().forEach(scc -> {
            var cycleValue = 0.0;
            // add the value in the cycle
            for (var i : scc) {
                cycleValue += jobWeights[i];
                for (var j : scc) {
                    if (follows.containsEdge(i, j)) {
                        cycleValue += edgeWeigths[i][j];
                    }
                }
            }
            for (var jobI : scc) {
                maxCycles[jobI] = Math.max(maxCycles[jobI], cycleValue);
            }
        });
        var mappedInspector = new ConnectivityInspector<>(mergedGraph);
        mappedInspector.connectedSets().forEach(wcc -> {
            // System.out.println("wcc: " + wcc);
            wcc.stream().mapToDouble(jobI -> jobWeights[jobI]).max().ifPresent(maxValue -> {
                for (var jobI : wcc) {
                    maxCycles[jobI] = maxValue;
                }
            });
        });
        // System.out.println("maxCycles: " + Arrays.toString(maxCycles));
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
