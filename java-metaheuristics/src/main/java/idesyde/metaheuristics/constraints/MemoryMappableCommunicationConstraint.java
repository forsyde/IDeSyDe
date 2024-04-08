package idesyde.metaheuristics.constraints;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;
import io.jenetics.util.ISeq;

public class MemoryMappableCommunicationConstraint<T extends Comparable<? super T>>
                implements Constraint<IntegerGene, T> {

        private final List<String> processors;
        private final List<String> memories;
        private final List<String> comms;
        private final List<String> tasks;
        private final List<String> buffers;
        private final Map<String, Map<String, List<String>>> preComputedPaths;
        private final Set<AperiodicAsynchronousDataflow> aperiodicAsynchronousDataflows;
        // public AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
        // decisionModel;

        public MemoryMappableCommunicationConstraint(
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
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
                aperiodicAsynchronousDataflows = decisionModel.aperiodicAsynchronousDataflows();
                preComputedPaths = decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths();
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
                                                        for (var ce : preComputedPaths
                                                                        .getOrDefault(pe, Map.of())
                                                                        .getOrDefault(me, List.of())) {
                                                                // we go through the communication elements in
                                                                // the path and ensure they have
                                                                // reservations
                                                                var k = comms.indexOf(ce);
                                                                if (k > -1 && reservations.get(peI * comms.size() + k)
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
                                                        if (aperiodicAsynchronousDataflows
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
                                                                for (var ce : preComputedPaths
                                                                                .getOrDefault(pe, Map.of())
                                                                                .getOrDefault(me, List.of())) {
                                                                        // we go through the communication
                                                                        // elements in the path and ensure they
                                                                        // have
                                                                        // reservations
                                                                        var k = comms.indexOf(ce);
                                                                        if (k > -1 && reservations.get(
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
                                                        if (aperiodicAsynchronousDataflows
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
                                                                for (var ce : preComputedPaths
                                                                                .getOrDefault(pe, Map.of())
                                                                                .getOrDefault(me, List.of())) {
                                                                        // we go through the communication
                                                                        // elements in the path and ensure they
                                                                        // have
                                                                        // reservations
                                                                        var k = comms.indexOf(ce);
                                                                        if (k > -1 && reservations.get(
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
                                reservations.stream().map(x -> false).toList());
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
                                                        for (var ce : preComputedPaths
                                                                        .getOrDefault(pe, Map.of())
                                                                        .getOrDefault(me, List.of())) {
                                                                // we go through the communication elements in
                                                                // the path and ensure they have
                                                                // reservations
                                                                var k = comms.indexOf(ce);
                                                                if (k > -1 && reservations.get(peI * comms.size() + k)
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
                                                        if (aperiodicAsynchronousDataflows
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
                                                                for (var ce : preComputedPaths
                                                                                .getOrDefault(pe, Map.of())
                                                                                .getOrDefault(me, List.of())) {
                                                                        // we go through the communication
                                                                        // elements in the path and ensure they
                                                                        // have
                                                                        // reservations
                                                                        var k = comms.indexOf(ce);
                                                                        if (k > -1 && reservations.get(
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
                                                        if (aperiodicAsynchronousDataflows
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
                                                                for (var ce : preComputedPaths
                                                                                .getOrDefault(pe, Map.of())
                                                                                .getOrDefault(me, List.of())) {
                                                                        // we go through the communication
                                                                        // elements in the path and ensure they
                                                                        // have
                                                                        // reservations
                                                                        var k = comms.indexOf(ce);
                                                                        if (k > -1 && reservations.get(
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
                                                                                ? IntegerGene.of(Math.max(reservations
                                                                                                .get(idx).allele(), 1),
                                                                                                reservations
                                                                                                                .get(idx)
                                                                                                                .min(),
                                                                                                reservations
                                                                                                                .get(idx)
                                                                                                                .max())
                                                                                : reservations.get(idx))
                                                                .collect(ISeq.toISeq())),
                                                individual.genotype().get(4)),
                                generation);
        }
}
