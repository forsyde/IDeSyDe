package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Constraint;
import io.jenetics.engine.Engine;
import io.jenetics.engine.InvertibleCodec;
import io.jenetics.engine.Limits;
import io.jenetics.engine.RetryConstraint;
import io.jenetics.ext.moea.UFTournamentSelector;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface CanExploreAADPMMMWithJenetics {

        default InvertibleCodec<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore, IntegerGene> ofDecisionModel(
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
                // these are up here to try to minimize memory allocations
                var procs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.processes().stream())
                                .toList();
                var bufs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.buffers().stream())
                                .toList();
                var pes = decisionModel.partitionedMemMappableMulticore().hardware().processingElems().stream()
                                .toList();
                var scheds = decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().stream().toList();
                var mems = decisionModel.partitionedMemMappableMulticore().hardware().storageElems().stream().toList();
                var jobs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.jobsOfProcesses().stream()).toList();
                var coms = decisionModel.partitionedMemMappableMulticore().hardware().communicationElems().stream()
                                .toList();
                return InvertibleCodec.of(
                                () -> {
                                        var taskMappingChromossome = IntegerChromosome.of(1,
                                                        decisionModel.partitionedMemMappableMulticore().hardware()
                                                                        .storageElems().size() + 1,
                                                        procs.size());
                                        var taskSchedulingChromossome = IntegerChromosome.of(1,
                                                        decisionModel.partitionedMemMappableMulticore().runtimes()
                                                                        .runtimes().size() + 1,
                                                        procs.size());
                                        var bufferMappingChromossome = IntegerChromosome.of(1,
                                                        decisionModel.partitionedMemMappableMulticore().hardware()
                                                                        .storageElems().size() + 1,
                                                        bufs.size());
                                        var channelReservationsChromossome = IntegerChromosome.of(0,
                                                        decisionModel.partitionedMemMappableMulticore().hardware()
                                                                        .communicationElementsMaxChannels().values()
                                                                        .stream()
                                                                        .mapToInt(x -> x).max().orElse(0) + 1,
                                                        decisionModel.partitionedMemMappableMulticore().hardware()
                                                                        .processingElems().size()
                                                                        * decisionModel.partitionedMemMappableMulticore()
                                                                                        .hardware().communicationElems()
                                                                                        .size());
                                        var jobOrderingChromossome = IntegerChromosome.of(
                                                        1,
                                                        decisionModel.aperiodicAsynchronousDataflows().stream()
                                                                        .mapToInt(a -> a.jobsOfProcesses().size()).sum()
                                                                        + 1,
                                                        jobs.size());
                                        return Genotype.of(taskMappingChromossome, taskSchedulingChromossome,
                                                        bufferMappingChromossome,
                                                        channelReservationsChromossome, jobOrderingChromossome);
                                },
                                gt -> {
                                        var taskMapping = IntStream.range(0, gt.get(0).length()).boxed()
                                                        .collect(Collectors.toMap(
                                                                        procs::get,
                                                                        idx -> mems.get(gt.get(0).get(idx).allele()
                                                                                        - 1)));
                                        var taskScheduling = IntStream.range(0, gt.get(1).length()).boxed()
                                                        .collect(Collectors.toMap(
                                                                        procs::get,
                                                                        idx -> scheds.get(gt.get(1).get(idx).allele()
                                                                                        - 1)));
                                        var bufferMapping = IntStream.range(0, gt.get(2).length()).boxed()
                                                        .collect(Collectors.toMap(
                                                                        bufs::get,
                                                                        idx -> mems.get(gt.get(2).get(idx).allele()
                                                                                        - 1)));
                                        Map<String, Map<String, Integer>> channelReservations = IntStream
                                                        .range(0, pes.size())
                                                        .boxed()
                                                        .collect(Collectors.toMap(
                                                                        bufI -> pes.get(bufI),
                                                                        bufI -> IntStream.range(0, coms.size())
                                                                                        .boxed()
                                                                                        .collect(Collectors.toMap(
                                                                                                        ceI -> coms.get(ceI),
                                                                                                        ceI -> gt.get(3).get(
                                                                                                                        coms.size() * bufI
                                                                                                                                        + ceI)
                                                                                                                        .allele()))));
                                        var superLoopSchedules = scheds.stream().collect(Collectors.toMap(
                                                        k -> k,
                                                        k -> IntStream.range(0, gt.get(4).length()).boxed()
                                                                        .filter(idx -> taskScheduling
                                                                                        .get(jobs.get(idx).process())
                                                                                        .equals(k))
                                                                        .map(idx -> jobs.get(idx).process())
                                                                        .collect(Collectors.toList())));
                                        return new AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore(
                                                        decisionModel.aperiodicAsynchronousDataflows(),
                                                        decisionModel.partitionedMemMappableMulticore(),
                                                        decisionModel.instrumentedComputationTimes(),
                                                        taskScheduling,
                                                        taskMapping,
                                                        bufferMapping,
                                                        superLoopSchedules,
                                                        channelReservations);
                                },
                                m -> {
                                        var model = (AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) m;
                                        var taskMappingChromossome = IntegerChromosome
                                                        .of(model.processesToMemoryMapping().entrySet().stream()
                                                                        .map(e -> IntegerGene.of(
                                                                                        mems.indexOf(e.getValue()), 1,
                                                                                        mems.size()))
                                                                        .collect(ISeq.toISeq()));
                                        var taskSchedulingChromossome = IntegerChromosome
                                                        .of(model.processesToRuntimeScheduling().entrySet().stream()
                                                                        .map(e -> IntegerGene.of(
                                                                                        scheds.indexOf(e.getValue()), 1,
                                                                                        scheds.size()))
                                                                        .collect(ISeq.toISeq()));
                                        var bufferMappingChromossome = IntegerChromosome
                                                        .of(model.bufferToMemoryMappings().entrySet().stream()
                                                                        .map(e -> IntegerGene.of(
                                                                                        mems.indexOf(e.getValue()), 1,
                                                                                        mems.size()))
                                                                        .collect(ISeq.toISeq()));
                                        var channelReservationsChromossome = IntegerChromosome
                                                        .of(model.processingElementsToRoutersReservations().entrySet()
                                                                        .stream()
                                                                        .flatMap(e -> e.getValue().entrySet().stream()
                                                                                        .map(ee -> IntegerGene.of(ee
                                                                                                        .getValue(), 0,
                                                                                                        decisionModel.partitionedMemMappableMulticore()
                                                                                                                        .hardware()
                                                                                                                        .communicationElementsMaxChannels()
                                                                                                                        .get(ee.getKey()))))
                                                                        .collect(ISeq.toISeq()));
                                        var orderings = new int[jobs.size()];
                                        Arrays.fill(orderings, -1);
                                        for (var sched : decisionModel.superLoopSchedules().keySet()) {
                                                var looplist = decisionModel.superLoopSchedules().get(sched);
                                                IntStream.range(0, looplist.size()).forEach(entryI -> {
                                                        var entry = looplist.get(entryI);
                                                        IntStream.range(0, jobs.size())
                                                                        .filter(j -> model
                                                                                        .processesToRuntimeScheduling()
                                                                                        .get(entry) == sched
                                                                                        && orderings[j] == -1
                                                                                        && jobs.get(j).process() == entry)
                                                                        .boxed()
                                                                        .min((a, b) -> (int) jobs.get(a).instance()
                                                                                        - (int) jobs.get(b).instance())
                                                                        .ifPresent(j -> orderings[j] = entryI);
                                                });
                                        }
                                        var jobOrderingChromossome = IntegerChromosome.of(IntStream
                                                        .range(0, jobs.size())
                                                        .mapToObj(j -> IntegerGene.of(orderings[j], 1, jobs.size()))
                                                        .collect(ISeq.toISeq()));
                                        return Genotype.of(taskMappingChromossome, taskSchedulingChromossome,
                                                        bufferMappingChromossome,
                                                        channelReservationsChromossome, jobOrderingChromossome);
                                });
        }

        default Stream<ExplorationSolution> exploreAADPMMM(
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel,
                        Set<ExplorationSolution> previousSolutions, Explorer.Configuration configuration) {
                var codec = ofDecisionModel(decisionModel);
                var engine = Engine.builder(this::evaluateAADPMMM, codec)
                                .offspringSelector(new TournamentSelector<>(5))
                                .survivorsSelector(UFTournamentSelector.ofVec())
                                .constraint(new CommunicationConstraint<>(decisionModel))
                                .constraint(new JobOrderingConstraint<>(decisionModel))
                                .constraint(RetryConstraint.of(codec, this::mappingIsFeasible))
                                .minimizing()
                                .build();
                var solStream = engine.stream().limit(Limits.byGeneConvergence(0.001, 1.0));
                var timedSolStream = configuration.totalExplorationTimeOutInSecs > 0L
                                ? solStream
                                                .limit(Limits.byExecutionTime(Duration.ofSeconds(
                                                                configuration.totalExplorationTimeOutInSecs)))
                                : solStream;
                var limitedSolStream = configuration.maximumSolutions > 0L
                                ? timedSolStream.limit(configuration.maximumSolutions)
                                : timedSolStream;
                return limitedSolStream
                                .map(sol -> {
                                        var solMap = new HashMap<String, Double>(sol.bestFitness().length());
                                        var bestFit = sol.bestFitness().data();
                                        solMap.put("nUsedPEs", bestFit[0]);
                                        var i = 0;
                                        for (var app : decisionModel.aperiodicAsynchronousDataflows()) {
                                                for (var actor : app.processes()) {
                                                        solMap.put("invThroughput(%s)".formatted(actor),
                                                                        bestFit[i + 1]);
                                                        i += 1;
                                                }
                                        }
                                        return new ExplorationSolution(
                                                        solMap,
                                                        codec.decode(sol.bestPhenotype().genotype()));
                                });
        }

        private Vec<double[]> evaluateAADPMMM(
                        final AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
                // first, get the number of used PEs
                var nUsedPEs = decisionModel.processesToRuntimeScheduling().values().stream().distinct().count();
                // get durations for each process
                var jobs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.jobsOfProcesses().stream()).toList();
                var follows = jobs.stream()
                                .map(src -> IntStream.range(0, jobs.size())
                                                .filter(dstI -> decisionModel.aperiodicAsynchronousDataflows().stream()
                                                                .anyMatch(app -> app.isSucessor(src, jobs.get(dstI))))
                                                .boxed().collect(Collectors.toSet()))
                                .collect(Collectors.toList());
                var scheds = decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().stream().toList();
                var mappings = IntStream.range(0, jobs.size()).map(
                                jobI -> scheds.indexOf(decisionModel.processesToRuntimeScheduling()
                                                .get(jobs.get(jobI).process())))
                                .toArray();
                var execTimes = jobs.stream().mapToLong(j -> {
                        var hostSched = decisionModel.processesToRuntimeScheduling().get(j.process());
                        var hostPe = decisionModel.partitionedMemMappableMulticore().runtimes().runtimeHost()
                                        .get(hostSched);
                        return decisionModel.instrumentedComputationTimes().worstExecutionTimes()
                                        .get(j.process()).getOrDefault(hostPe, Long.MAX_VALUE);
                })
                                .mapToDouble(
                                                w -> w != Long.MAX_VALUE
                                                                ? (double) w / decisionModel
                                                                                .instrumentedComputationTimes()
                                                                                .scaleFactor().doubleValue()
                                                                : Double.POSITIVE_INFINITY)
                                .toArray();
                var orderings = new int[jobs.size()];
                Arrays.fill(orderings, -1);
                for (var sched : decisionModel.superLoopSchedules().keySet()) {
                        var schedI = scheds.indexOf(sched);
                        var looplist = decisionModel.superLoopSchedules().get(sched);
                        IntStream.range(0, looplist.size()).forEach(entryI -> {
                                var entry = looplist.get(entryI);
                                IntStream.range(0, jobs.size())
                                                .filter(j -> mappings[j] == schedI && orderings[j] == -1
                                                                && jobs.get(j).process() == entry)
                                                .boxed()
                                                .min((a, b) -> (int) jobs.get(a).instance()
                                                                - (int) jobs.get(b).instance())
                                                .ifPresent(j -> orderings[j] = entryI);
                        });
                }

                var edgeWeights = new double[jobs.size()][jobs.size()];
                for (int srcI = 0; srcI < jobs.size(); srcI++) {
                        var src = jobs.get(srcI);
                        var srcHostSched = decisionModel.processesToRuntimeScheduling().get(src.process());
                        var srcHostPe = decisionModel.partitionedMemMappableMulticore().runtimes().runtimeHost()
                                        .get(srcHostSched);
                        for (int dstI = 0; dstI < jobs.size(); dstI++) {
                                var dst = jobs.get(dstI);
                                var dstHostSched = decisionModel.processesToRuntimeScheduling().get(dst.process());
                                var dstHostPe = decisionModel.partitionedMemMappableMulticore().runtimes().runtimeHost()
                                                .get(dstHostSched);
                                // var sentDataTime =
                                // decisionModel.aperiodicAsynchronousDataflows().stream().map(app -> )
                                edgeWeights[srcI][dstI] = 0.0;
                        }
                }
                var cycleLengths = recomputeMaximumCycles(follows,
                                IntStream.range(0, jobs.size()).map(
                                                jobI -> scheds.indexOf(decisionModel.processesToMemoryMapping()
                                                                .get(jobs.get(jobI).process())))
                                                .toArray(),
                                orderings, execTimes, edgeWeights);
                var invThroughputs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.processes().stream())
                                // add the throughputs to the decision model for the sake of later reference
                                .map(actor -> {
                                        var maxRepetitions = jobs.stream()
                                                        .filter(j -> j.process().equals(actor))
                                                        .mapToLong(j -> j.instance()).max().orElse(1L);
                                        return IntStream.range(0, jobs.size())
                                                        .filter(j -> jobs.get(j).process().equals(actor))
                                                        .mapToDouble(j -> cycleLengths[j] / maxRepetitions)
                                                        .max().orElse(0.0);
                                })
                                .collect(Collectors.toList());
                var objs = new double[invThroughputs.size() + 1];
                objs[0] = (double) nUsedPEs;
                for (int i = 0; i < invThroughputs.size(); i++) {
                        objs[i + 1] = invThroughputs.get(i);
                }
                return Vec.of(objs);
        }

        private double[] recomputeMaximumCycles(
                        final List<Set<Integer>> follows,
                        final int[] mapping,
                        final int[] ordering,
                        final double[] jobWeights,
                        final double[][] edgeWeigths) {
                BiFunction<Integer, Integer, Boolean> mustSuceed = (i, j) -> {
                        return mapping[i] == mapping[j] ? ordering[i] + 1 == ordering[j] : follows.get(i).contains(j);
                };
                BiFunction<Integer, Integer, Boolean> mustCycle = (i, j) -> {
                        return mapping[i] == mapping[j] ? ordering[j] == 1 && ordering[i] > 1 : false;
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
                                                if (mustSuceed.apply(i, j) | mustCycle.apply(i, j)) {
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
                                if (mustSuceed.apply(i, j) | mustCycle.apply(i, j)) {
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

        class CommunicationConstraint<T extends Comparable<? super T>> implements Constraint<IntegerGene, T> {

                private final List<String> processors;
                private final List<String> memories;
                private final List<String> comms;
                private final List<String> tasks;
                private final List<String> buffers;
                public AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel;

                public CommunicationConstraint(
                                AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
                        this.decisionModel = decisionModel;
                        processors = decisionModel.partitionedMemMappableMulticore().hardware().processingElems()
                                        .stream().toList();
                        memories = decisionModel.partitionedMemMappableMulticore().hardware().storageElems().stream()
                                        .toList();
                        comms = decisionModel.partitionedMemMappableMulticore().hardware().communicationElems().stream()
                                        .toList();
                        tasks = decisionModel.aperiodicAsynchronousDataflows().stream()
                                        .flatMap(x -> x.processes().stream())
                                        .collect(Collectors.toList());
                        buffers = decisionModel.aperiodicAsynchronousDataflows().stream()
                                        .flatMap(x -> x.buffers().stream())
                                        .collect(Collectors.toList());
                }

                @Override
                public boolean test(Phenotype<IntegerGene, T> individual) {
                        var taskMapping = individual.genotype().get(0);
                        var taskScheduling = individual.genotype().get(1);
                        var bufferMapping = individual.genotype().get(2);
                        var reservations = individual.genotype().get(3);
                        for (int taskI = 0; taskI < tasks.size(); taskI++) {
                                var task = tasks.get(taskI);
                                for (int peI = 0; peI < processors.size(); peI++) {
                                        var pe = processors.get(peI);
                                        if (taskScheduling.get(taskI).allele() == peI) { // a task is mapped in PE i
                                                for (int meI = 0; meI < memories.size(); meI++) {
                                                        var me = memories.get(meI);
                                                        if (taskMapping.get(taskI).allele() == meI) { // the data is
                                                                                                      // mapped to a ME
                                                                                                      // j
                                                                for (var ce : decisionModel
                                                                                .partitionedMemMappableMulticore()
                                                                                .hardware()
                                                                                .preComputedPaths()
                                                                                .getOrDefault(pe, Map.of())
                                                                                .getOrDefault(me, List.of())) {
                                                                        // we go through the communication elements in
                                                                        // the path and ensure they have
                                                                        // reservations
                                                                        var k = comms.indexOf(ce);
                                                                        if (reservations.get(peI * comms.size() + k)
                                                                                        .allele() <= 0) { // should but
                                                                                                          // it is
                                                                                                          // not
                                                                                                          // reserved
                                                                                return false;
                                                                        }
                                                                }
                                                        }
                                                        for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                                                var buffer = buffers.get(bufferI);
                                                                if (decisionModel.aperiodicAsynchronousDataflows()
                                                                                .stream().anyMatch(app -> app
                                                                                                .processGetFromBufferInBits()
                                                                                                .getOrDefault(task, Map
                                                                                                                .of())
                                                                                                .containsKey(buffer))
                                                                                && bufferMapping.get(bufferI)
                                                                                                .allele() == meI) { // task
                                                                                                                    // reads
                                                                                                                    // from
                                                                                                                    // buffer
                                                                        for (var ce : decisionModel
                                                                                        .partitionedMemMappableMulticore()
                                                                                        .hardware()
                                                                                        .preComputedPaths()
                                                                                        .getOrDefault(pe, Map.of())
                                                                                        .getOrDefault(me, List.of())) {
                                                                                // we go through the communication
                                                                                // elements in the path and ensure they
                                                                                // have
                                                                                // reservations
                                                                                var k = comms.indexOf(ce);
                                                                                if (reservations.get(
                                                                                                peI * comms.size() + k)
                                                                                                .allele() <= 0) { // should
                                                                                                                  // but
                                                                                                                  // it
                                                                                                                  // is
                                                                                                                  // not
                                                                                                                  // reserved
                                                                                        return false;
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                        for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                                                var buffer = buffers.get(bufferI);
                                                                if (decisionModel.aperiodicAsynchronousDataflows()
                                                                                .stream().anyMatch(app -> app
                                                                                                .processPutInBufferInBits()
                                                                                                .getOrDefault(task, Map
                                                                                                                .of())
                                                                                                .containsKey(buffer))
                                                                                && bufferMapping.get(bufferI)
                                                                                                .allele() == meI) { // task
                                                                                                                    // writes
                                                                                                                    // to
                                                                                                                    // buffer
                                                                        for (var ce : decisionModel
                                                                                        .partitionedMemMappableMulticore()
                                                                                        .hardware()
                                                                                        .preComputedPaths()
                                                                                        .getOrDefault(pe, Map.of())
                                                                                        .getOrDefault(me, List.of())) {
                                                                                // we go through the communication
                                                                                // elements in the path and ensure they
                                                                                // have
                                                                                // reservations
                                                                                var k = comms.indexOf(ce);
                                                                                if (reservations.get(
                                                                                                peI * comms.size() + k)
                                                                                                .allele() <= 0) { // should
                                                                                                                  // but
                                                                                                                  // it
                                                                                                                  // is
                                                                                                                  // not
                                                                                                                  // reserved
                                                                                        return false;
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                        return true;
                }

                @Override
                public Phenotype<IntegerGene, T> repair(Phenotype<IntegerGene, T> individual, long generation) {
                        var taskMapping = individual.genotype().get(0);
                        var taskScheduling = individual.genotype().get(1);
                        var bufferMapping = individual.genotype().get(2);
                        var reservations = individual.genotype().get(3);
                        var changeReservations = new java.util.ArrayList<>(
                                        individual.genotype().get(4).stream().map(x -> false).toList());
                        for (int taskI = 0; taskI < tasks.size(); taskI++) {
                                var task = tasks.get(taskI);
                                for (int peI = 0; peI < processors.size(); peI++) {
                                        var pe = processors.get(peI);
                                        if (taskScheduling.get(taskI).allele() == peI) { // a task is mapped in PE i
                                                for (int meI = 0; meI < memories.size(); meI++) {
                                                        var me = memories.get(meI);
                                                        if (taskMapping.get(taskI).allele() == meI) { // the data is
                                                                                                      // mapped to a ME
                                                                                                      // j
                                                                for (var ce : decisionModel
                                                                                .partitionedMemMappableMulticore()
                                                                                .hardware()
                                                                                .preComputedPaths()
                                                                                .getOrDefault(pe, Map.of())
                                                                                .getOrDefault(me, List.of())) {
                                                                        // we go through the communication elements in
                                                                        // the path and ensure they have
                                                                        // reservations
                                                                        var k = comms.indexOf(ce);
                                                                        if (reservations.get(peI * comms.size() + k)
                                                                                        .allele() <= 0) { // should but
                                                                                                          // it is
                                                                                                          // not
                                                                                                          // reserved
                                                                                changeReservations.set(
                                                                                                peI * comms.size() + k,
                                                                                                true);
                                                                        }
                                                                }
                                                        }
                                                        for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                                                var buffer = buffers.get(bufferI);
                                                                if (decisionModel.aperiodicAsynchronousDataflows()
                                                                                .stream().anyMatch(app -> app
                                                                                                .processGetFromBufferInBits()
                                                                                                .getOrDefault(task, Map
                                                                                                                .of())
                                                                                                .containsKey(buffer))
                                                                                && bufferMapping.get(bufferI)
                                                                                                .allele() == meI) { // task
                                                                                                                    // reads
                                                                                                                    // from
                                                                                                                    // buffer
                                                                        for (var ce : decisionModel
                                                                                        .partitionedMemMappableMulticore()
                                                                                        .hardware()
                                                                                        .preComputedPaths()
                                                                                        .getOrDefault(pe, Map.of())
                                                                                        .getOrDefault(me, List.of())) {
                                                                                // we go through the communication
                                                                                // elements in the path and ensure they
                                                                                // have
                                                                                // reservations
                                                                                var k = comms.indexOf(ce);
                                                                                if (reservations.get(
                                                                                                peI * comms.size() + k)
                                                                                                .allele() <= 0) { // should
                                                                                                                  // but
                                                                                                                  // it
                                                                                                                  // is
                                                                                                                  // not
                                                                                                                  // reserved
                                                                                        changeReservations.set(peI
                                                                                                        * comms.size()
                                                                                                        + k, true);
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                        for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                                                var buffer = buffers.get(bufferI);
                                                                if (decisionModel.aperiodicAsynchronousDataflows()
                                                                                .stream().anyMatch(app -> app
                                                                                                .processPutInBufferInBits()
                                                                                                .getOrDefault(task, Map
                                                                                                                .of())
                                                                                                .containsKey(buffer))
                                                                                && bufferMapping.get(bufferI)
                                                                                                .allele() == meI) { // task
                                                                                                                    // writes
                                                                                                                    // to
                                                                                                                    // buffer
                                                                        for (var ce : decisionModel
                                                                                        .partitionedMemMappableMulticore()
                                                                                        .hardware()
                                                                                        .preComputedPaths()
                                                                                        .getOrDefault(pe, Map.of())
                                                                                        .getOrDefault(me, List.of())) {
                                                                                // we go through the communication
                                                                                // elements in the path and ensure they
                                                                                // have
                                                                                // reservations
                                                                                var k = comms.indexOf(ce);
                                                                                if (reservations.get(
                                                                                                peI * comms.size() + k)
                                                                                                .allele() <= 0) { // should
                                                                                                                  // but
                                                                                                                  // it
                                                                                                                  // is
                                                                                                                  // not
                                                                                                                  // reserved
                                                                                        changeReservations.set(peI
                                                                                                        * comms.size()
                                                                                                        + k, true);
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                        return Phenotype.of(
                                        Genotype.of(
                                                        taskMapping,
                                                        taskScheduling,
                                                        bufferMapping,
                                                        IntegerChromosome.of(IntStream.range(0, reservations.length())
                                                                        .mapToObj(idx -> changeReservations.get(idx)
                                                                                        ? IntegerGene.of(1, reservations
                                                                                                        .get(idx).max()
                                                                                                        + 1)
                                                                                        : reservations.get(idx))
                                                                        .collect(ISeq.toISeq())),
                                                        individual.genotype().get(4)),
                                        generation);
                }
        }

        class JobOrderingConstraint<T extends Comparable<? super T>> implements Constraint<IntegerGene, T> {

                private final List<String> runtimes;
                private final List<String> tasks;
                private final List<AperiodicAsynchronousDataflow.Job> jobs;
                private final Map<AperiodicAsynchronousDataflow.Job, Set<AperiodicAsynchronousDataflow.Job>> memoizedSucessors;
                public AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel;

                public JobOrderingConstraint(
                                AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
                        this.decisionModel = decisionModel;
                        runtimes = decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().stream()
                                        .toList();
                        tasks = decisionModel.aperiodicAsynchronousDataflows().stream()
                                        .flatMap(x -> x.processes().stream())
                                        .collect(Collectors.toList());
                        jobs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                        .flatMap(x -> x.jobsOfProcesses().stream())
                                        .collect(Collectors.toList());
                        memoizedSucessors = jobs.stream().collect(Collectors.toMap(
                                        j -> j,
                                        j -> jobs.stream()
                                                        .filter(jj -> decisionModel.aperiodicAsynchronousDataflows()
                                                                        .stream()
                                                                        .anyMatch(app -> app.isSucessor(j, jj)))
                                                        .collect(Collectors.toSet())));
                }

                @Override
                public boolean test(Phenotype<IntegerGene, T> individual) {
                        var taskScheduling = individual.genotype().get(1);
                        var jobOrderings = individual.genotype().get(4);
                        return IntStream.range(0, runtimes.size()).anyMatch(schedI -> {
                                var mappedJobs = IntStream.range(0, jobs.size())
                                                .filter(jobI -> taskScheduling
                                                                .get(tasks.indexOf(jobs.get(jobI).process()))
                                                                .allele() == schedI)
                                                .toArray();
                                // the first condition checks if the jobs are clashing in the same ordering
                                // position
                                // the second checks whether they follow the parial order in the following way:
                                // isSucessor(j, jj) -> order(j) < order(jj)
                                // equivalently,
                                // !isSucessor(j, jj) or order(j) < order(jj)
                                return Arrays.stream(mappedJobs)
                                                .anyMatch(j -> Arrays.stream(mappedJobs)
                                                                .anyMatch(jj -> (jobOrderings.get(j)
                                                                                .allele() == jobOrderings.get(jj)
                                                                                                .allele())
                                                                                ||
                                                                                (!memoizedSucessors.get(jobs.get(j))
                                                                                                .contains(jobs.get(jj))
                                                                                                || jobOrderings.get(j)
                                                                                                                .allele() < jobOrderings
                                                                                                                                .get(jj)
                                                                                                                                .allele())));
                        });
                }

                @Override
                public Phenotype<IntegerGene, T> repair(Phenotype<IntegerGene, T> individual, long generation) {
                        var taskScheduling = individual.genotype().get(1);
                        var jobOrderings = individual.genotype().get(4);
                        var newJobOrderings = new ArrayList<IntegerGene>(jobs.size());
                        IntStream.range(0, runtimes.size()).forEach(schedI -> {
                                var mappedJobs = IntStream.range(0, jobs.size())
                                                .filter(jobI -> taskScheduling
                                                                .get(tasks.indexOf(jobs.get(jobI).process()))
                                                                .allele() == schedI)
                                                .toArray();
                                // sor the chromosome so that
                                var newChromosome = Arrays.stream(mappedJobs)
                                                .boxed()
                                                .sorted((j, jj) -> memoizedSucessors.get(jobs.get(j))
                                                                .contains(jobs.get(jj)) ? -1
                                                                                : jobOrderings.get(j).compareTo(
                                                                                                jobOrderings.get(jj)))
                                                .collect(Collectors.toList());
                                for (int i = 0; i < mappedJobs.length; i++) {
                                        newJobOrderings.set(mappedJobs[i],
                                                        IntegerGene.of(newChromosome.get(i), 1, mappedJobs.length));
                                }
                        });
                        return Phenotype.of(
                                        Genotype.of(
                                                        individual.genotype().get(0),
                                                        taskScheduling,
                                                        individual.genotype().get(2),
                                                        individual.genotype().get(3),
                                                        IntegerChromosome.of(newJobOrderings)),
                                        generation);
                }

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
                });
        }

}
