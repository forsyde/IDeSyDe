package idesyde.metaheuristics.constraints;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;

import java.util.*;
import java.util.stream.Collectors;

public class FeasibleMappingConstraint<T extends Comparable<? super T>>
        implements Constraint<IntegerGene, T> {

    private final int taskSchedulingGenotypeIdx;
    private final List<List<Integer>> allowedMappings;

    private final int numMappables;

    public FeasibleMappingConstraint(
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
        taskSchedulingGenotypeIdx = 1;
        var schedulers = decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().stream().toList();
        numMappables = schedulers.size();
        allowedMappings = decisionModel
                .aperiodicAsynchronousDataflows().stream().flatMap(app -> app.processes().stream()).map(
                        proc -> decisionModel.instrumentedComputationTimes().worstExecutionTimes().get(proc).keySet()
                                .stream().map(pe ->
                                        schedulers.indexOf(decisionModel.partitionedMemMappableMulticore().runtimes().processorAffinities().get(pe)))
                                .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public FeasibleMappingConstraint(
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel) {
        taskSchedulingGenotypeIdx = 0;
        var schedulers = decisionModel.partitionedTiledMulticore().runtimes().runtimes().stream().toList();
        numMappables = schedulers.size();
        allowedMappings = decisionModel
                .aperiodicAsynchronousDataflows().stream().flatMap(app -> app.processes().stream()).map(
                        proc -> decisionModel.instrumentedComputationTimes().worstExecutionTimes().get(proc).keySet()
                                .stream().map(pe ->
                                        schedulers.indexOf(decisionModel.partitionedTiledMulticore().runtimes().processorAffinities().get(pe)))
                                .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean test(Phenotype<IntegerGene, T> individual) {
        for (int i = 0; i < individual.genotype().get(taskSchedulingGenotypeIdx).length(); i++) {
            var gene = individual.genotype().get(taskSchedulingGenotypeIdx).get(i);
            if (!allowedMappings.get(i).contains(gene.allele())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Phenotype<IntegerGene, T> repair(Phenotype<IntegerGene, T> individual, long generation) {
        var repaired = new ArrayList<IntegerGene>(individual.genotype().get(taskSchedulingGenotypeIdx).length());
        for (int i = 0; i < individual.genotype().get(taskSchedulingGenotypeIdx).length(); i++) {
            var gene = individual.genotype().get(taskSchedulingGenotypeIdx).get(i);
            var processAllowed = allowedMappings.get(i);
            if (!processAllowed.contains(gene.allele())) {
                var randomPE = processAllowed.get(Math.floorMod(generation, processAllowed.size()));
                repaired.add(IntegerGene.of(randomPE, 0, numMappables));
            } else {
                repaired.add(gene);
            }
        }
        var chromossomes = individual.genotype().stream().collect(Collectors.toList());
        chromossomes.set(taskSchedulingGenotypeIdx, IntegerChromosome.of(repaired));
        return Phenotype.of(Genotype.of(chromossomes), generation);
    }
}
