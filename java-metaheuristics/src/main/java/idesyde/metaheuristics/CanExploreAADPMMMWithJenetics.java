package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import io.jenetics.*;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Constraint;
import io.jenetics.engine.Engine;
import io.jenetics.ext.moea.UFTournamentSelector;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface CanExploreAADPMMMWithJenetics {

    default Codec<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore, IntegerGene> ofDecisionModel(AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
        // these are up here to try to minimize memory allocations
        var procs = decisionModel.aperiodicAsynchronousDataflows().stream().flatMap(app -> app.processes().stream()).toList();
        System.out.println(procs.toString());
        var bufs = decisionModel.aperiodicAsynchronousDataflows().stream().flatMap(app -> app.buffers().stream()).toList();
        var scheds = decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().stream().toList();
        var mems = decisionModel.partitionedMemMappableMulticore().hardware().storageElems().stream().toList();
        var jobs = decisionModel.aperiodicAsynchronousDataflows().stream().flatMap(app -> app.jobsOfProcesses().stream()).toList();
        return Codec.of(
                () -> {
                    var taskMappingChromossome = IntegerChromosome.of(1, decisionModel.partitionedMemMappableMulticore().hardware().storageElems().size() + 1, decisionModel.aperiodicAsynchronousDataflows().stream().mapToInt(x -> x.processes().size()).sum());
                    var taskSchedulingChromossome = IntegerChromosome.of(1, decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().size() + 1, decisionModel.aperiodicAsynchronousDataflows().stream().mapToInt(x -> x.processes().size()).sum());
                    var bufferMappingChromossome = IntegerChromosome.of(1, decisionModel.partitionedMemMappableMulticore().hardware().storageElems().size()+ 1, decisionModel.aperiodicAsynchronousDataflows().stream().mapToInt(x -> x.buffers().size()).sum());
                    var channelReservationsChromossome = IntegerChromosome.of(0,
                            decisionModel.partitionedMemMappableMulticore().hardware().communicationElementsMaxChannels().stream().mapToInt(x -> x).max().orElse(0) + 1,
                            decisionModel.partitionedMemMappableMulticore().hardware().processingElems().size() * decisionModel.partitionedMemMappableMulticore().hardware().communicationElems().size());
                    var jobOrderingChromossome = IntegerChromosome.of(
                            1,
                            decisionModel.aperiodicAsynchronousDataflows().stream().mapToInt(a -> a.jobsOfProcesses().size()).sum() + 1,
                            decisionModel.aperiodicAsynchronousDataflows().stream().mapToInt(a -> a.jobsOfProcesses().size()).sum()
                    );
                    return Genotype.of(taskMappingChromossome, taskSchedulingChromossome, bufferMappingChromossome, channelReservationsChromossome, jobOrderingChromossome);
                },
                gt -> {
                    var taskMapping = IntStream.range(0, gt.get(0).length()).boxed().collect(Collectors.toMap(
                            procs::get,
                            idx -> mems.get(gt.get(0).get(idx).allele())
                    ));
                    var taskScheduling = IntStream.range(0, gt.get(1).length()).boxed().collect(Collectors.toMap(
                            bufs::get,
                            idx -> scheds.get(gt.get(0).get(idx).allele())
                    ));
                    var bufferMapping = IntStream.range(0, gt.get(2).length()).boxed().collect(Collectors.toMap(
                            bufs::get,
                            idx -> mems.get(gt.get(0).get(idx).allele())
                    ));
                    var superLoopSchedules = scheds.stream().collect(Collectors.toMap(
                            k -> k,
                            k -> IntStream.range(0, gt.get(3).length()).boxed().filter(idx ->
                                    taskScheduling.get(jobs.get(idx).process()).equals(k)
                            ).map(idx -> jobs.get(idx).process()).collect(Collectors.toList())
                    ));
                    return new AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore(
                            decisionModel.aperiodicAsynchronousDataflows(),
                            decisionModel.partitionedMemMappableMulticore(),
                            decisionModel.instrumentedComputationTimes(),
                            taskScheduling,
                            taskMapping,
                            bufferMapping,
                            superLoopSchedules,
                            Map.of()
                    );
                }
        );
    }

    default Stream<ExplorationSolution> exploreAADPMMM(AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel, Set<ExplorationSolution> previousSolutions, Explorer.Configuration configuration) {
        var codec = ofDecisionModel(decisionModel);
        var engine = Engine.builder(this::evaluateAADPMMM, codec)
                .offspringSelector(new TournamentSelector<>())
                .survivorsSelector(UFTournamentSelector.ofVec())
                .constraint(new CommunicationConstraint<>(decisionModel))
                .minimizing()
                .build();
        return engine.stream()
                .limit(1000)
                .map(sol -> new ExplorationSolution(
                                Map.of("nUsedPEs", sol.bestFitness().data()[0]),
                                codec.decode(sol.bestPhenotype().genotype())
                        )
                );
    }

    private Vec<double[]> evaluateAADPMMM(final AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
        // first, get the number of used PEs
        var nUsedPEs = decisionModel.processesToRuntimeScheduling().values().stream().distinct().count();
        // get durations for each process
        return Vec.of(((double) nUsedPEs));
    }

    class CommunicationConstraint<T extends Comparable<? super T>> implements Constraint<IntegerGene, T> {

        private final List<String> processors;
        private final List<String> memories;
        private final List<String> comms;
        private final List<String> tasks;
        private final List<String> buffers;
        public AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel;

        public CommunicationConstraint(AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
            this.decisionModel = decisionModel;
            processors = decisionModel.partitionedMemMappableMulticore().hardware().processingElems().stream().toList();
            memories = decisionModel.partitionedMemMappableMulticore().hardware().storageElems().stream().toList();
            comms = decisionModel.partitionedMemMappableMulticore().hardware().communicationElems().stream().toList();
            tasks = decisionModel.aperiodicAsynchronousDataflows().stream().flatMap(x -> x.processes().stream()).collect(Collectors.toList());
            buffers = decisionModel.aperiodicAsynchronousDataflows().stream().flatMap(x -> x.buffers().stream()).collect(Collectors.toList());
        }

        @Override
        public boolean test(Phenotype<IntegerGene, T> individual) {
            var taskMapping = individual.genotype().get(0);
            var taskScheduling = individual.genotype().get(1);
            var bufferMapping = individual.genotype().get(3);
            var reservations = individual.genotype().get(4);
            for (int taskI = 0; taskI < tasks.size(); taskI++) {
                var task = tasks.get(taskI);
                for (int peI = 0; peI < processors.size(); peI++) {
                    var pe = processors.get(peI);
                    if (taskScheduling.get(taskI).allele() == peI) { // a task is mapped in PE i
                        for (int meI = 0; meI < memories.size(); meI++) {
                            var me = memories.get(meI);
                            if (taskMapping.get(taskI).allele() == meI) { // the data is mapped to a ME j
                                for (var ce : decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths().getOrDefault(pe, Map.of()).getOrDefault(me, List.of())) {
                                    // we go through the communication elements in the path and ensure they have reservations
                                    var k = comms.indexOf(ce);
                                    if (reservations.get(peI * comms.size() + k).allele() <= 0) { // should but it is not reserved
                                        return false;
                                    }
                                }
                            }
                            for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                var buffer = buffers.get(bufferI);
                                if (decisionModel.aperiodicAsynchronousDataflows().stream().anyMatch(app -> app.processGetFromBufferInBits().getOrDefault(task, Map.of()).containsKey(buffer)) && bufferMapping.get(bufferI).allele() == meI) { // task reads from buffer
                                    for (var ce : decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths().getOrDefault(pe, Map.of()).getOrDefault(me, List.of())) {
                                        // we go through the communication elements in the path and ensure they have reservations
                                        var k = comms.indexOf(ce);
                                        if (reservations.get(peI * comms.size() + k).allele() <= 0) { // should but it is not reserved
                                            return false;
                                        }
                                    }
                                }
                            }
                            for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                var buffer = buffers.get(bufferI);
                                if (decisionModel.aperiodicAsynchronousDataflows().stream().anyMatch(app -> app.processPutInBufferInBits().getOrDefault(task, Map.of()).containsKey(buffer)) && bufferMapping.get(bufferI).allele() == meI) { // task writes to buffer
                                    for (var ce : decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths().getOrDefault(pe, Map.of()).getOrDefault(me, List.of())) {
                                        // we go through the communication elements in the path and ensure they have reservations
                                        var k = comms.indexOf(ce);
                                        if (reservations.get(peI * comms.size() + k).allele() <= 0) { // should but it is not reserved
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
            var bufferMapping = individual.genotype().get(3);
            var reservations = individual.genotype().get(4);
            var changeReservations = new java.util.ArrayList<>(individual.genotype().get(4).stream().map(x -> false).toList());
            for (int taskI = 0; taskI < tasks.size(); taskI++) {
                var task = tasks.get(taskI);
                for (int peI = 0; peI < processors.size(); peI++) {
                    var pe = processors.get(peI);
                    if (taskScheduling.get(taskI).allele() == peI) { // a task is mapped in PE i
                        for (int meI = 0; meI < memories.size(); meI++) {
                            var me = memories.get(meI);
                            if (taskMapping.get(taskI).allele() == meI) { // the data is mapped to a ME j
                                for (var ce : decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths().getOrDefault(pe, Map.of()).getOrDefault(me, List.of())) {
                                    // we go through the communication elements in the path and ensure they have reservations
                                    var k = comms.indexOf(ce);
                                    if (reservations.get(peI * comms.size() + k).allele() <= 0) { // should but it is not reserved
                                        changeReservations.set(peI * comms.size() + k, true);
                                    }
                                }
                            }
                            for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                var buffer = buffers.get(bufferI);
                                if (decisionModel.aperiodicAsynchronousDataflows().stream().anyMatch(app -> app.processGetFromBufferInBits().getOrDefault(task, Map.of()).containsKey(buffer)) && bufferMapping.get(bufferI).allele() == meI) { // task reads from buffer
                                    for (var ce : decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths().getOrDefault(pe, Map.of()).getOrDefault(me, List.of())) {
                                        // we go through the communication elements in the path and ensure they have reservations
                                        var k = comms.indexOf(ce);
                                        if (reservations.get(peI * comms.size() + k).allele() <= 0) { // should but it is not reserved
                                            changeReservations.set(peI * comms.size() + k, true);
                                        }
                                    }
                                }
                            }
                            for (int bufferI = 0; bufferI < buffers.size(); bufferI++) {
                                var buffer = buffers.get(bufferI);
                                if (decisionModel.aperiodicAsynchronousDataflows().stream().anyMatch(app -> app.processPutInBufferInBits().getOrDefault(task, Map.of()).containsKey(buffer)) && bufferMapping.get(bufferI).allele() == meI) { // task writes to buffer
                                    for (var ce : decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths().getOrDefault(pe, Map.of()).getOrDefault(me, List.of())) {
                                        // we go through the communication elements in the path and ensure they have reservations
                                        var k = comms.indexOf(ce);
                                        if (reservations.get(peI * comms.size() + k).allele() <= 0) { // should but it is not reserved
                                            changeReservations.set(peI * comms.size() + k, true);
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
                            IntegerChromosome.of(IntStream.range(0, reservations.length()).mapToObj(idx -> changeReservations.get(idx) ? IntegerGene.of(1, reservations.get(idx).max() + 1) : reservations.get(idx)).collect(ISeq.toISeq()))
                    ),
                    generation
            );
        }
    }
}
