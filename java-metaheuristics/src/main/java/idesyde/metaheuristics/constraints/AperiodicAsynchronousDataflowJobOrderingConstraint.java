package idesyde.metaheuristics.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import io.jenetics.Chromosome;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;

public class AperiodicAsynchronousDataflowJobOrderingConstraint<T extends Comparable<? super T>>
                implements Constraint<IntegerGene, T> {

        private final List<String> runtimes;
        private final List<String> tasks;
        private final List<AperiodicAsynchronousDataflow.Job> jobs;
        private final Map<AperiodicAsynchronousDataflow.Job, Set<AperiodicAsynchronousDataflow.Job>> memoizedSucessors;
        private final int taskSchedulingGenotypeIdx;
        private final int jobOrderingGenotypeIdx;

        public AperiodicAsynchronousDataflowJobOrderingConstraint(
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
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
                taskSchedulingGenotypeIdx = 1;
                jobOrderingGenotypeIdx = 4;
        }

        public AperiodicAsynchronousDataflowJobOrderingConstraint(
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel) {
                runtimes = decisionModel.partitionedTiledMulticore().runtimes().runtimes().stream()
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
                taskSchedulingGenotypeIdx = 0;
                jobOrderingGenotypeIdx = 2;
        }

        @Override
        public boolean test(Phenotype<IntegerGene, T> individual) {
                var taskScheduling = individual.genotype().get(taskSchedulingGenotypeIdx);
                var jobOrderings = individual.genotype().get(jobOrderingGenotypeIdx);
                return IntStream.range(0, runtimes.size()).allMatch(schedI -> {
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
                        for (var j : mappedJobs) {
                                for (var jj : mappedJobs) {
                                        if (j != jj) {
                                                if (jobOrderings.get(j).allele() == jobOrderings.get(jj).allele()) {
                                                        return false;
                                                } else if (memoizedSucessors.get(jobs.get(j)).contains(jobs.get(jj)) && jobOrderings.get(j)
                                                                .allele() > jobOrderings.get(jj).allele()) {
                                                        return false;
                                                } else if (memoizedSucessors.get(jobs.get(jj)).contains(jobs.get(j)) && jobOrderings.get(j)
                                                                .allele() < jobOrderings.get(jj).allele()) {
                                                        return false;
                                                }
                                        }
                                }
                        }
                        // var isValid = Arrays.stream(mappedJobs)
                        // .allMatch(j -> Arrays.stream(mappedJobs)
                        // .filter(jj -> j != jj)
                        // .allMatch(jj -> (jobOrderings.get(j)
                        // .allele() != jobOrderings.get(jj)
                        // .allele())
                        // &&
                        // (!memoizedSucessors.get(jobs.get(j))
                        // .contains(jobs.get(jj))
                        // || jobOrderings.get(j)
                        // .allele() < jobOrderings
                        // .get(jj)
                        // .allele())));
                        // !isSucessor(j, jj) or order(j) < order(jj)
                        // if (!isValid) {
                        // System.out.println(Arrays.stream(mappedJobs)
                        // .mapToObj(x -> jobs.get(x))
                        // .map(a -> "(%s, %s)".formatted(a.process(), a.instance()))
                        // .reduce((a, b) -> a + ", " + b));
                        // }
                        return true;
                });
        }

        @Override
        public Phenotype<IntegerGene, T> repair(Phenotype<IntegerGene, T> individual, long generation) {
                var chromossomes = individual.genotype().stream().collect(Collectors.toList());
                var taskScheduling = chromossomes.get(taskSchedulingGenotypeIdx);
                var jobOrderings = chromossomes.get(jobOrderingGenotypeIdx);
                var newJobOrderings = jobOrderings.stream().collect(Collectors.toList());
                IntStream.range(0, runtimes.size()).forEach(schedI -> {
                        var mappedJobs = IntStream.range(0, jobs.size())
                                        .filter(jobI -> taskScheduling
                                                        .get(tasks.indexOf(jobs.get(jobI).process()))
                                                        .allele() == schedI)
                                        .toArray();
                        // System.out.println(Arrays.stream(mappedJobs)
                        // .mapToObj(a -> String.valueOf(a))
                        // .reduce((a, b) -> a + ", " + b) + " "
                        // + Arrays.stream(mappedJobs)
                        // .mapToObj(x -> jobs.get(x))
                        // .map(a -> "(%s, %s)".formatted(a.process(), a.instance()))
                        // .reduce((a, b) -> a + ", " + b));
                        // sor the chromosome so that
                        var newChromosome = Arrays.stream(mappedJobs)
                                        .boxed()
                                        .sorted((j, jj) -> memoizedSucessors.get(jobs.get(j))
                                                        .contains(jobs.get(jj)) ? -1
                                                                        : memoizedSucessors.get(jobs.get(jj))
                                                                                        .contains(jobs.get(j)) ? 1 : 0)
                                        .collect(Collectors.toList());
                        // System.out.println(newChromosome.stream()
                        // .map(a -> a.toString())
                        // .reduce((a, b) -> a + ", " + b) + " "
                        // + newChromosome.stream().map(x -> jobs.get(x))
                        // .map(a -> "(%s, %s)".formatted(a.process(), a.instance()))
                        // .reduce((a, b) -> a + ", " + b));
                        for (int i = 0; i < newChromosome.size(); i++) {
                                newJobOrderings.set(newChromosome.get(i),
                                                IntegerGene.of(i, 0, jobs.size()));
                        }
                });
                chromossomes.set(jobOrderingGenotypeIdx, IntegerChromosome.of(newJobOrderings));
                return Phenotype.of(Genotype.of(chromossomes), generation);
        }

}
