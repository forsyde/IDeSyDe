package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.*;
import org.jgrapht.traverse.BreadthFirstIterator;

interface AperiodicAsynchronousDataflowMethods {

    default double[] recomputeMaximumCycles(
            final Graph<Integer, DefaultEdge> follows,
            final int[] mapping,
            final int[] ordering,
            final double[] jobWeights,
            final double[][] edgeWeigths) {
        var numJobs = mapping.length;
        var mappingGraph = new DefaultDirectedWeightedGraph<Integer, DefaultEdge>(DefaultEdge.class);
        for (int i = 0; i < numJobs; i++) {
            mappingGraph.addVertex(i);
            for (var e : follows.outgoingEdgesOf(i)) {
                var j = follows.getEdgeTarget(e);
                mappingGraph.addVertex(j);
                mappingGraph.addVertex(numJobs*(i + 1) + j);
                mappingGraph.addEdge(i, numJobs*(i + 1) + j);
                mappingGraph.setEdgeWeight(i, numJobs*(i + 1) + j, jobWeights[i]);
                mappingGraph.addEdge(numJobs*(i + 1) + j, j);
                mappingGraph.setEdgeWeight(numJobs*(i + 1) + j, j, edgeWeigths[i][j]);
            }
        }
        // now overlay the ordering parts
        for (int i = 0; i < numJobs; i++) {
            for (int j = 0; j < numJobs; j++) {
                if (mapping[i] == mapping[j] && ordering[i] + 1 == ordering[j]) {
                    mappingGraph.addVertex(i);
                    mappingGraph.addVertex(j);
                    mappingGraph.addEdge(i, j);
                    mappingGraph.setEdgeWeight(i, j, jobWeights[i]);
                    // including the ordering of messages
                    for (var e : follows.outgoingEdgesOf(i)) {
                        var k = follows.getEdgeTarget(e);
                        for (var ee : follows.outgoingEdgesOf(j)) {
                            var l = follows.getEdgeTarget(ee);
                            mappingGraph.addEdge(numJobs*(i + 1) + k, numJobs*(j + 1) + l);
                            mappingGraph.setEdgeWeight(numJobs*(i + 1) + k, numJobs*(j + 1) + l, edgeWeigths[i][k]);
                        }
                    }
                } else if (mapping[i] == mapping[j] && ordering[j] == 0 && ordering[i] > 0) {
                    mappingGraph.addVertex(i);
                    mappingGraph.addVertex(j);
                    mappingGraph.addEdge(i, j);
                    mappingGraph.setEdgeWeight(i, j, jobWeights[i]);
                    // including the ordering of messages
                    for (var e : follows.outgoingEdgesOf(i)) {
                        var k = follows.getEdgeTarget(e);
                        for (var ee : follows.outgoingEdgesOf(j)) {
                            var l = follows.getEdgeTarget(ee);
                            mappingGraph.addEdge(numJobs*(i + 1) + k, numJobs*(j + 1) + l);
                            mappingGraph.setEdgeWeight(numJobs*(i + 1) + k, numJobs*(j + 1) + l, edgeWeigths[i][k]);
                        }
                    }
                }
            }
        }
        // System.out.println(
        // "mappings is %s and weights is %s".formatted(Arrays.toString(mapping), Arrays.toString(jobWeights)));
        // System.out.println("edge weights are %s".formatted(Arrays.stream(edgeWeigths).map(Arrays::toString).reduce("", (a, b) -> a + "," + b)));
        // var mergedGraph = new AsGraphUnion<>(follows, mappingGraph);
        var maxCycles = new double[numJobs * (numJobs + 1)];
        // var invThroughputs = new double[numJobs * (numJobs + 1)];
        for (int i = 0; i < numJobs; i++) {
            maxCycles[i] = 0.0;
            // invThroughputs[i] = jobWeights[i] + IntStream.range(0, numJobs).mapToDouble(j -> edgeWeigths[finalI][j]).sum();
        }
        for (int i = numJobs; i < numJobs; i++) {
            for (int j = 0; j < numJobs; j++) {
                maxCycles[numJobs* (i + 1) + j] = 0.0;
                // invThroughputs[numJobs* (i + 1) + j] = edgeWeigths[i][j];
            }
        }
        var sccAlgorithm = new KosarajuStrongConnectivityInspector<>(mappingGraph);
        sccAlgorithm.stronglyConnectedSets().forEach(scc -> {
            // System.out.println("scc: " + scc);
            if (scc.size() > 1) {
                var sccGraph = new AsSubgraph<>(mappingGraph, scc);
                var simpleCycles = new JohnsonSimpleCycles<>(sccGraph);
                var cycles = simpleCycles.findSimpleCycles();
                cycles.forEach(cycle -> {
                    // System.out.println("Cycle is %s".formatted(cycle));
                    var cycleVal = sccGraph.getEdgeWeight(sccGraph.getEdge(cycle.get(cycle.size() - 1), cycle.get(0)));
                    for (int i = 0; i < cycle.size() - 1; i++) {
                        // System.out.println("Edge is %s".formatted(sccGraph.getEdge(cycle.get(i), cycle.get(i + 1))));
                        cycleVal += sccGraph.getEdgeWeight(sccGraph.getEdge(cycle.get(i), cycle.get(i + 1)));
                    }
                    // System.out.println("Cycle is %s with value %f".formatted(cycle, cycleVal));
                    for (int v : cycle) {
                        maxCycles[v] = Math.max(maxCycles[v], cycleVal);
                    }
                });
        //         // var pivotOpt = scc.stream().filter(x -> x < numJobs).findFirst(); // there should be at least one
        //         // pivotOpt.ifPresent(pivot -> {
                
        //         //     var bfs = new BreadthFirstIterator<>(sccGraph, pivot);
        //         //     // bfs.next(); // skip the first one
        //         //     // System.out.println("Pivot is %d".formatted(pivot));
        //         //     while (bfs.hasNext()) {
        //         //         var next = bfs.next();
        //         //         // try to find the maximum cycle
        //         //         if (sccGraph.containsEdge(next, pivot)) {
        //         //             var cur = next;
        //         //             var prev = bfs.getParent(next);
        //         //             var cycleVal = sccGraph.getEdgeWeight(sccGraph.getEdge(cur, pivot));
        //         //             // System.out.println("Itearting closed cycle in %s".formatted(cur));
        //         //             // System.out.println("Cur is %d, Prev is %s".formatted(cur, prev));
        //         //             while (cur != pivot) {
        //         //                 // System.out.println("Cur is %d, Prev is %s".formatted(cur, prev));
        //         //                 cycleVal += sccGraph.getEdgeWeight(sccGraph.getEdge(prev, cur));
        //         //                 cur = prev;
        //         //                 prev = bfs.getParent(cur);
        //         //             }
        //         //             maxCycles[pivot] = Math.max(maxCycles[pivot], cycleVal);
        //         //         }
        //         //         // System.out.println("Next is %d".formatted(next));
        //         //         // for (var e : sccGraph.incomingEdgesOf(next)) {
        //         //         //     var prev = sccGraph.getEdgeSource(e);
        //         //         //     System.out.println("Prev is %d with edge %f, cur is %f".formatted(prev, sccGraph.getEdgeWeight(e), maxCycles[prev]));
        //         //         //     maxCycles[next] = Math.max(maxCycles[next], maxCycles[prev] + sccGraph.getEdgeWeight(e));
        //         //         // }
        //         //     }
        //         //     // add the value in the cycle
        //         //     // for (var e : sccGraph.incomingEdgesOf(pivot)) {
        //         //     //     var prev = sccGraph.getEdgeSource(e);
        //         //     //     System.out.println("Closing cycle with %d and edge %f".formatted(prev, sccGraph.getEdgeWeight(e)));
        //         //     //     maxCycles[pivot] = Math.max(maxCycles[pivot], maxCycles[prev] + sccGraph.getEdgeWeight(e));
        //         //     // }
        //         // });
            }
        });
        // now add cycles created by the transmission of messages
        for (int i = 0; i < numJobs; i++) {
            var finalI = i;
            maxCycles[i] = Math.max(maxCycles[i], jobWeights[i] + IntStream.range(0, numJobs).mapToDouble(j -> edgeWeigths[finalI][j]).sum());
        }
        // System.out.println("A maxCycles: " + Arrays.toString(maxCycles));
        var mappedInspector = new ConnectivityInspector<>(mappingGraph);
        mappedInspector.connectedSets().forEach(wcc -> {
            // System.out.println("wcc: " + wcc);
            wcc.stream().mapToDouble(jobI -> maxCycles[jobI]).max().ifPresent(maxValue -> {
                for (var jobI : wcc) {
                    maxCycles[jobI] = maxValue;
                }
            });
        });
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
