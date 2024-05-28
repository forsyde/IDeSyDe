package idesyde.forsydeio;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.hierarchy.behavior.moc.sdf.SDFChannel;
import forsyde.io.lib.hierarchy.implementation.functional.BufferLike;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL;
import idesyde.core.AutoRegister;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.ReverseIdentificationRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.AsGraphUnion;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

@AutoRegister(ForSyDeIOModule.class)
public class AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPLRev
                implements ReverseIdentificationRule {

        private record Pair<A, B>(A fst, B snd) {
        }

        private Set<DesignModel> innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPl(
                        Set<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL> solvedModels,
                        Set<ForSyDeIODesignModel> designModels) {
                return solvedModels.stream().map(model -> {
                        var numJobs = model.aperiodicAsynchronousDataflows().stream()
                                        .mapToInt(app -> app.jobGraphName().size()).sum();
                        var reversedSystemGraph = new SystemGraph();
                        var jobGraph = new DefaultDirectedGraph<Pair<String, Long>, DefaultEdge>(DefaultEdge.class);
                        var jobMapping = new HashMap<Pair<String, Long>, String>();
                        var jobOrdering = new HashMap<Pair<String, Long>, Integer>();
                        for (var app : model.aperiodicAsynchronousDataflows()) {
                                for (int i = 0; i < app.jobGraphSrcName().size(); i++) {
                                        var src = new Pair<>(app.jobGraphSrcName().get(i),
                                                        app.jobGraphSrcInstance().get(i));
                                        var dst = new Pair<>(app.jobGraphDstName().get(i),
                                                        app.jobGraphDstInstance().get(i));
                                        jobGraph.addVertex(src);
                                        jobGraph.addVertex(dst);
                                        jobGraph.addEdge(src, dst);

                                }
                        }
                        model.processesToMemoryMapping().forEach((process, mem) -> {
                                var procVertex = reversedSystemGraph.newVertex(process);
                                var memVertex = reversedSystemGraph.newVertex(mem);
                                var memMapped = ForSyDeHierarchy.MemoryMapped
                                                .enforce(reversedSystemGraph,
                                                                procVertex);
                                memMapped.mappingHost(
                                                ForSyDeHierarchy.GenericMemoryModule
                                                                .enforce(reversedSystemGraph,
                                                                                memVertex));
                                ForSyDeHierarchy.GreyBox.enforce(
                                                reversedSystemGraph,
                                                memVertex)
                                                .addContained(ForSyDeHierarchy.Visualizable
                                                                .enforce(memMapped));
                        });
                        model.bufferToMemoryMappings().forEach((buf, mem) -> {
                                var bufVertex = reversedSystemGraph.newVertex(buf);
                                var memVertex = reversedSystemGraph.newVertex(mem);
                                var memMapped = ForSyDeHierarchy.MemoryMapped
                                                .enforce(reversedSystemGraph,
                                                                bufVertex);
                                memMapped.mappingHost(
                                                ForSyDeHierarchy.GenericMemoryModule
                                                                .enforce(reversedSystemGraph,
                                                                                memVertex));
                                ForSyDeHierarchy.GreyBox.enforce(
                                                reversedSystemGraph,
                                                memVertex)
                                                .addContained(ForSyDeHierarchy.Visualizable
                                                                .enforce(memMapped));
                        });
                        model.processesToRuntimeScheduling().forEach((process, sched) -> {
                                for (var app : model.aperiodicAsynchronousDataflows()) {
                                        for (int i = 0; i < app.jobGraphSrcName().size(); i++) {
                                                if (process.equals(app.jobGraphSrcName().get(i))) {
                                                        var src = new Pair<>(app.jobGraphSrcName().get(i),
                                                                        app.jobGraphSrcInstance().get(i));
                                                        jobMapping.put(src, sched);
                                                }
                                        }
                                }
                                var procVertex = reversedSystemGraph.newVertex(process);
                                var schedVertex = reversedSystemGraph.newVertex(sched);
                                var scheduled = ForSyDeHierarchy.Scheduled
                                                .enforce(reversedSystemGraph,
                                                                procVertex);
                                scheduled.runtimeHost(
                                                ForSyDeHierarchy.AbstractRuntime
                                                                .enforce(reversedSystemGraph,
                                                                                schedVertex));
                                ForSyDeHierarchy.GreyBox.enforce(
                                                reversedSystemGraph,
                                                schedVertex)
                                                .addContained(ForSyDeHierarchy.Visualizable
                                                                .enforce(scheduled));

                        });
                        model.processesToLogicProgrammableAreas().forEach((process, pla) -> {
                                for (var app : model.aperiodicAsynchronousDataflows()) {
                                        for (int i = 0; i < app.jobGraphSrcName().size(); i++) {
                                                if (process.equals(app.jobGraphSrcName().get(i))) {
                                                        var src = new Pair<>(app.jobGraphSrcName().get(i),
                                                                        app.jobGraphSrcInstance().get(i));
                                                        jobMapping.put(src, pla);
                                                }
                                        }
                                }
                                var procVertex = reversedSystemGraph.newVertex(process);
                                var plaVertex = reversedSystemGraph.newVertex(pla);
                                ForSyDeHierarchy.LogicProgrammableSynthetized.enforce(reversedSystemGraph, procVertex)
                                                .hostLogicProgrammableModule(
                                                                ForSyDeHierarchy.LogicProgrammableModule.enforce(
                                                                                reversedSystemGraph, plaVertex));
                                ForSyDeHierarchy.GreyBox.enforce(reversedSystemGraph, plaVertex).addContained(
                                                ForSyDeHierarchy.Visualizable.enforce(reversedSystemGraph, procVertex));
                        });
                        var currentInstances = new HashMap<String, Integer>();
                        for (var app : model.aperiodicAsynchronousDataflows()) {
                                for (var process : app.processes()) {
                                        currentInstances.put(process, 0);
                                }
                        }
                        model.superLoopSchedules().forEach((sched, looplist) -> {
                                for (int idx = 0; idx < looplist.size(); idx++) {
                                        var entry = looplist.get(idx);
                                        var instance = currentInstances.get(entry);
                                        jobOrdering.put(new Pair<String, Long>(entry, Long.valueOf(instance)), idx);
                                }
                                var schedVertex = reversedSystemGraph.newVertex(sched);
                                var scheduler = ForSyDeHierarchy.SuperLoopRuntime
                                                .enforce(reversedSystemGraph, schedVertex);
                                scheduler.superLoopEntries(looplist);
                        });
                        var recomputedTimes = recomputeExecutionTimes(model);
                        var maximumCycles = recomputeMaximumCycles(jobGraph, jobMapping, jobOrdering, recomputedTimes,
                                        Map.of());
                        var originalSDFChannels = new HashMap<String, BufferLike>();
                        designModels.stream()
                                        .flatMap(x -> ForSyDeIODesignModel.tryFrom(x).stream())
                                        .forEach(x -> {
                                                var sg = x.systemGraph();
                                                sg.vertexSet().stream().flatMap(v -> ForSyDeHierarchy.BufferLike
                                                                .tryView(sg, v).stream()).forEach(c -> {
                                                                        originalSDFChannels.put(c.getIdentifier(), c);
                                                                });
                                        });
                        model.aperiodicAsynchronousDataflows()
                                        .forEach(app -> {
                                                var maxAppCycle = maximumCycles.entrySet().stream()
                                                                .filter(e -> app.processes().contains(e.getKey().fst()))
                                                                .mapToDouble(e -> e.getValue()).max().orElse(0.0);
                                                var invMaxAppCycle = 1.0 / maxAppCycle;
                                                var scale = 1.0;
                                                while (invMaxAppCycle * scale < 0.1) {
                                                        scale *= 10.0;
                                                }
                                                for (var actor : app.processes()) {
                                                        var repetitions = maximumCycles.entrySet().stream()
                                                                        .filter(e -> e.getKey().fst().equals(actor))
                                                                        .mapToLong(e -> e.getKey().snd()).max()
                                                                        .orElse(1L);
                                                        var process = reversedSystemGraph.newVertex(actor);
                                                        var behaviour = ForSyDeHierarchy.AnalyzedBehavior
                                                                        .enforce(reversedSystemGraph, process);
                                                        behaviour.throughputInSecsDenominator(Math.round(scale));
                                                        behaviour.throughputInSecsNumerator(
                                                                        repetitions * Math
                                                                                        .round(invMaxAppCycle * scale));
                                                }
                                                app.buffers().forEach(channel -> {
                                                        var channelVec = reversedSystemGraph.newVertex(channel);
                                                        var bbuf = ForSyDeHierarchy.BoundedBufferLike
                                                                        .enforce(reversedSystemGraph, channelVec);
                                                        var channelBuf = ForSyDeHierarchy.SDFChannel.enforce(bbuf);
                                                        var consumer = app.processes().stream()
                                                                        .filter(p -> app.processGetFromBufferInBits()
                                                                                        .get(p)
                                                                                        .containsKey(channel))
                                                                        .findFirst().orElse("");
                                                        var consumerMaxInstance = IntStream
                                                                        .range(0, app.jobGraphName().size())
                                                                        .filter(i -> app.jobGraphName().get(i)
                                                                                        .equals(consumer))
                                                                        .mapToLong(i -> app.jobGraphInstance().get(i))
                                                                        .max()
                                                                        .orElse(1);
                                                        var maxSize = app.processGetFromBufferInBits().get(consumer)
                                                                        .getOrDefault(channel, 0L)
                                                                        * consumerMaxInstance;
                                                        var original = originalSDFChannels.get(channel);
                                                        if (original.elementSizeInBits() > 0L) {
                                                                bbuf.maxElements(channelBuf.numInitialTokens()
                                                                                + (int) (maxSize
                                                                                                / original.elementSizeInBits()));
                                                                bbuf.elementSizeInBits(original.elementSizeInBits());
                                                        }
                                                });
                                        });
                        return new ForSyDeIODesignModel(reversedSystemGraph);
                }).collect(Collectors.toSet());
        }

        @Override
        public Set<DesignModel> apply(Set<? extends DecisionModel> t, Set<? extends DesignModel> u) {
                var filteredSolved = t.stream()
                                .flatMap(x -> DecisionModel.cast(x,
                                                AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL.class)
                                                .stream())
                                .collect(Collectors.toSet());
                var filteredDesign = u.stream()
                                .flatMap(x -> ForSyDeIODesignModel.tryFrom(x).stream())
                                .collect(Collectors.toSet());
                return innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPl(
                                filteredSolved,
                                filteredDesign);
        }

        protected Map<Pair<String, Long>, Double> recomputeMaximumCycles(
                        final Graph<Pair<String, Long>, DefaultEdge> follows,
                        final Map<Pair<String, Long>, String> mapping,
                        final Map<Pair<String, Long>, Integer> ordering,
                        final Map<Pair<String, Long>, Double> jobWeights,
                        final Map<Pair<String, Long>, Map<Pair<String, Long>, Double>> edgeWeigths) {
                var mappingGraph = new DefaultDirectedGraph<Pair<String, Long>, DefaultEdge>(DefaultEdge.class);
                for (var src : mapping.keySet()) {
                        for (var dst : mapping.keySet()) {
                                if (src != dst && mapping.get(src) == mapping.get(dst)
                                                && ordering.getOrDefault(src, 0) + 1 == ordering.getOrDefault(dst, 0)) {
                                        mappingGraph.addVertex(src);
                                        mappingGraph.addVertex(dst);
                                        mappingGraph.addEdge(src, dst);
                                } else if (src != dst && mapping.get(src) == mapping.get(dst)
                                                && ordering.getOrDefault(dst, -1) == 0
                                                && ordering.getOrDefault(src, 0) > 0) {
                                        mappingGraph.addVertex(src);
                                        mappingGraph.addVertex(dst);
                                        mappingGraph.addEdge(src, dst);
                                }
                        }
                }
                var mergedGraph = new AsGraphUnion<>(follows, mappingGraph);
                var maxCycles = new HashMap<Pair<String, Long>, Double>();
                for (var jobI : jobWeights.keySet()) {
                        maxCycles.put(jobI, jobWeights.get(jobI));
                }
                var sccAlgorithm = new KosarajuStrongConnectivityInspector<>(mergedGraph);
                sccAlgorithm.stronglyConnectedSets().forEach(scc -> {
                        var cycleValue = 0.0;
                        // add the value in the cycle
                        for (var i : scc) {
                                cycleValue += jobWeights.get(i);
                                for (var j : scc) {
                                        if (follows.containsEdge(i, j)) {
                                                cycleValue += edgeWeigths.getOrDefault(i, Map.of()).getOrDefault(j,
                                                                0.0);
                                        }
                                }
                        }
                        // System.out.println("scc %s has %f".formatted(scc.toString(), cycleValue));
                        for (var jobI : scc) {
                                maxCycles.put(jobI, Math.max(maxCycles.get(jobI), cycleValue));
                        }
                });
                // System.out.println("A maxCycles: " + Arrays.toString(maxCycles));
                var mappedInspector = new ConnectivityInspector<>(mergedGraph);
                mappedInspector.connectedSets().forEach(wcc -> {
                        // System.out.println("wcc: " + wcc);
                        wcc.stream().mapToDouble(jobI -> maxCycles.get(jobI)).max().ifPresent(maxValue -> {
                                for (var jobI : wcc) {
                                        maxCycles.put(jobI, maxValue);
                                }
                        });
                });
                // System.out.println("B maxCycles: " + Arrays.toString(maxCycles));
                return maxCycles;
        }

        protected HashMap<Pair<String, Long>, Double> recomputeExecutionTimes(
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL model) {
                var times = new HashMap<Pair<String, Long>, Double>();
                for (var app : model.aperiodicAsynchronousDataflows()) {
                        for (int i = 0; i < app.jobGraphName().size(); i++) {
                                var job = new Pair<>(app.jobGraphName().get(i), app.jobGraphInstance().get(i));
                                if (model.processesToRuntimeScheduling().containsKey(job.fst())) {
                                        var weights = model.instrumentedComputationTimes().averageExecutionTimes()
                                                        .get(app.jobGraphName().get(i));
                                        var pe = model.partitionedMemMappableMulticoreAndPl().runtimes().runtimeHost()
                                                        .get(model.processesToRuntimeScheduling().get(job.fst()));
                                        times.put(job, Double.valueOf(weights.get(pe)) / Double
                                                        .valueOf(model.instrumentedComputationTimes().scaleFactor()));
                                        // now compute the time requried to fetch the process information from the
                                        // mapped memory to the executing processor
                                        if (model.processesToMemoryMapping().containsKey(job.fst())) {
                                                var mem = model.processesToMemoryMapping().get(job.fst());
                                                var path = model.partitionedMemMappableMulticoreAndPl().hardware()
                                                                .preComputedPaths().getOrDefault(mem, Map.of())
                                                                .getOrDefault(pe, List.of());
                                                var minBW = path.stream().mapToDouble(ce -> {
                                                        var slots = model
                                                                        .processingElementsToRoutersReservations()
                                                                        .getOrDefault(pe, Map.of()).getOrDefault(ce, 1);
                                                        var bw = model.partitionedMemMappableMulticoreAndPl().hardware()
                                                                        .communicationElementsBitPerSecPerChannel()
                                                                        .getOrDefault(ce, 1.0);
                                                        return bw * Double.valueOf(slots);
                                                }).min().orElse(1.0);
                                                var fetchTime = Double
                                                                .valueOf(model.instrumentedMemoryRequirements()
                                                                                .memoryRequirements().get(job.fst())
                                                                                .getOrDefault(pe, 0L))
                                                                * Double.valueOf(path.size()) / minBW;
                                                times.computeIfPresent(job, (k, v) -> v + fetchTime);
                                        }
                                } else if (model.processesToLogicProgrammableAreas().containsKey(job.fst())) {
                                        var pla = model.processesToLogicProgrammableAreas().get(job.fst());
                                        times.put(job,
                                                        Double.valueOf(model.hardwareImplementationArea()
                                                                        .latenciesNumerators().get(job.fst()).get(pla))
                                                                        /
                                                                        Double.valueOf(model
                                                                                        .hardwareImplementationArea()
                                                                                        .latenciesDenominators()
                                                                                        .get(job.fst()).get(pla)));
                                }
                                for (var readBuffer : app.processGetFromBufferInBits().get(job.fst()).keySet()) {
                                        var data = Double.valueOf(app.processGetFromBufferInBits().get(job.fst())
                                                        .getOrDefault(readBuffer, 0L));
                                        var pe = model.processesToRuntimeScheduling().containsValue(job.fst())
                                                        ? model.partitionedMemMappableMulticoreAndPl().runtimes()
                                                                        .runtimeHost()
                                                                        .get(model.processesToRuntimeScheduling()
                                                                                        .get(job.fst()))
                                                        : model.processesToLogicProgrammableAreas().get(job.fst());
                                        var mem = model.bufferToMemoryMappings().get(readBuffer);
                                        var path = model.partitionedMemMappableMulticoreAndPl().hardware()
                                                        .preComputedPaths().getOrDefault(mem, Map.of())
                                                        .getOrDefault(pe, List.of());
                                        var minBW = path.stream().mapToDouble(ce -> {
                                                var slots = model
                                                                .processingElementsToRoutersReservations()
                                                                .getOrDefault(pe, Map.of()).getOrDefault(ce, 1);
                                                var bw = model.partitionedMemMappableMulticoreAndPl().hardware()
                                                                .communicationElementsBitPerSecPerChannel()
                                                                .getOrDefault(ce, 1.0);
                                                return bw * Double.valueOf(slots);
                                        }).min().orElse(1.0);
                                        var readTime = data * Double.valueOf(path.size()) / minBW;
                                        times.computeIfPresent(job, (k, v) -> v + readTime);
                                }
                                for (var writeBuffer : app.processPutInBufferInBits().get(job.fst()).keySet()) {
                                        var data = Double.valueOf(app.processPutInBufferInBits().get(job.fst())
                                                        .getOrDefault(writeBuffer, 0L));
                                        var pe = model.processesToRuntimeScheduling().containsValue(job.fst())
                                                        ? model.partitionedMemMappableMulticoreAndPl().runtimes()
                                                                        .runtimeHost()
                                                                        .get(model.processesToRuntimeScheduling()
                                                                                        .get(job.fst()))
                                                        : model.processesToLogicProgrammableAreas().get(job.fst());
                                        var mem = model.bufferToMemoryMappings().get(writeBuffer);
                                        var path = model.partitionedMemMappableMulticoreAndPl().hardware()
                                                        .preComputedPaths().getOrDefault(pe, Map.of())
                                                        .getOrDefault(mem, List.of());
                                        var minBW = path.stream().mapToDouble(ce -> {
                                                var slots = model
                                                                .processingElementsToRoutersReservations()
                                                                .getOrDefault(pe, Map.of()).getOrDefault(ce, 1);
                                                var bw = model.partitionedMemMappableMulticoreAndPl().hardware()
                                                                .communicationElementsBitPerSecPerChannel()
                                                                .getOrDefault(ce, 1.0);
                                                return bw * Double.valueOf(slots);
                                        }).min().orElse(1.0);
                                        var readTime = data * Double.valueOf(path.size()) / minBW;
                                        times.computeIfPresent(job, (k, v) -> v + readTime);
                                }
                        }
                }
                return times;
        }
}
