package idesyde.metaheuristics.constraints;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;
import org.jgrapht.Graph;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

public class AperiodicAsynchronousDataflowJobOrderingConstraint<T extends Comparable<? super T>>
                implements Constraint<IntegerGene, T> {

        private final List<String> runtimes;
        private final List<String> tasks;
        private final List<AperiodicAsynchronousDataflow.Job> jobs;
        private final SimpleDirectedGraph<AperiodicAsynchronousDataflow.Job, DefaultEdge> jobGraph;

        private final ConnectivityInspector<AperiodicAsynchronousDataflow.Job, DefaultEdge> jobGraphInspector;
        private final int taskSchedulingGenotypeIdx;
        private final int jobOrderingGenotypeIdx;
        // private final List<List<Integer>> schedulersJobList;

        public Graph<AperiodicAsynchronousDataflow.Job, DefaultEdge> getJobGraph() {
                return jobGraph;
        }

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
                jobGraph = new SimpleDirectedGraph<>(DefaultEdge.class);
                decisionModel.aperiodicAsynchronousDataflows().forEach(app -> {
                        app.jobsOfProcesses().forEach(jobGraph::addVertex);
                        app.jobSucessors().forEach((job, succs) -> {
                                succs.forEach(succ -> jobGraph.addEdge(job, succ));
                        });
                });
                TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(jobGraph);
                jobGraphInspector = new ConnectivityInspector<>(jobGraph);
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
                jobGraph = new SimpleDirectedGraph<>(DefaultEdge.class);
                decisionModel.aperiodicAsynchronousDataflows().forEach(app -> {
                        app.jobsOfProcesses().forEach(jobGraph::addVertex);
                        app.jobSucessors().forEach((job, succs) -> {
                                succs.forEach(succ -> jobGraph.addEdge(job, succ));
                        });
                });
                TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(jobGraph);
                jobGraphInspector = new ConnectivityInspector<>(jobGraph);
                taskSchedulingGenotypeIdx = 0;
                jobOrderingGenotypeIdx = 2;
        }

        @Override
        public boolean test(Phenotype<IntegerGene, T> individual) {
                var taskScheduling = individual.genotype().get(taskSchedulingGenotypeIdx);
                var jobOrderings = individual.genotype().get(jobOrderingGenotypeIdx);
                for (int i = 0; i < jobs.size(); i++) {
                        var schedI = taskScheduling
                                .get(tasks.indexOf(jobs.get(i).process()))
                                .allele();
                        for (int j = 0; j < jobs.size(); j++) {
                                var schedJ = taskScheduling
                                        .get(tasks.indexOf(jobs.get(j).process()))
                                        .allele();
                                if (i != j && Objects.equals(schedI, schedJ) && jobGraph.containsEdge(jobs.get(i), jobs.get(j))) {
                                        if (jobOrderings.get(i)
                                                .allele() >= jobOrderings.get(j)
                                                .allele()) {
                                                return false;
                                        }
                                }
                        }
                }
                return true;
        }

        @Override
        public Phenotype<IntegerGene, T> repair(Phenotype<IntegerGene, T> individual, long generation) {
                var chromossomes = individual.genotype().stream().collect(Collectors.toList());
                var taskScheduling = chromossomes.get(taskSchedulingGenotypeIdx);
                var jobOrderings = chromossomes.get(jobOrderingGenotypeIdx);
                var newJobOrderings = jobOrderings.stream().collect(Collectors.toList());
                IntStream.range(0, runtimes.size()).forEach(schedI -> {
                        var mappedJobs = IntStream.range(0, jobs.size()).filter(
                                        jobI -> taskScheduling.get(tasks.indexOf(jobs.get(jobI).process()))
                                                        .allele() == schedI)
                                .mapToObj(jobs::get)
                                .collect(Collectors.toSet());
                        var subgraph = new AsSubgraph<>(jobGraph, mappedJobs);
                        var topoOrder = new TopologicalOrderIterator<>(subgraph);
                        var idx = 0;
                        while (topoOrder.hasNext()) {
                                var job = topoOrder.next();
                                var jobIdx = jobs.indexOf(job);
                                newJobOrderings.set(jobIdx, IntegerGene.of(idx, 0, jobs.size()));
                                idx += 1;
                        }
                });
                chromossomes.set(jobOrderingGenotypeIdx, IntegerChromosome.of(newJobOrderings));
                return Phenotype.of(Genotype.of(chromossomes), generation);
        }


}
