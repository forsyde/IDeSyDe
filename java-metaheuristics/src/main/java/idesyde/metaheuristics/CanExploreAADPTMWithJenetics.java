package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import idesyde.metaheuristics.constraints.AperiodicAsynchronousDataflowJobOrderingConstraint;
import idesyde.metaheuristics.constraints.FeasibleMappingConstraint;
import idesyde.metaheuristics.constraints.MultiConstraint;
import io.jenetics.*;
import io.jenetics.engine.Constraint;
import io.jenetics.engine.Engine;
import io.jenetics.engine.InvertibleCodec;
import io.jenetics.engine.Limits;
import io.jenetics.ext.moea.UFTournamentSelector;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface CanExploreAADPTMWithJenetics extends AperiodicAsynchronousDataflowMethods {

        default InvertibleCodec<AperiodicAsynchronousDataflowToPartitionedTiledMulticore, IntegerGene> ofDecisionModel(
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel) {
                // these are up here to try to minimize memory allocations
                var tasks = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.processes().stream())
                                .toList();
                var bufs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.buffers().stream())
                                .toList();
                var tiles = decisionModel.partitionedTiledMulticore().hardware().processors().stream()
                                .toList();
                var mems = decisionModel.partitionedTiledMulticore().hardware().memories().stream().toList();
                var scheds = decisionModel.partitionedTiledMulticore().runtimes().runtimes().stream().toList();
                var jobs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.jobsOfProcesses().stream()).toList();
                var coms = decisionModel.partitionedTiledMulticore().hardware().communicationElements().stream()
                                .toList();
                return InvertibleCodec.of(
                                () -> {
                                        var taskMappingAndSchedulingChromossome = IntegerChromosome.of(0,
                                                        scheds.size(),
                                                        tasks.size());
                                        var channelReservationsChromossome = IntegerChromosome.of(0,
                                                        decisionModel.partitionedTiledMulticore().hardware()
                                                                        .communicationElementsMaxChannels().values()
                                                                        .stream()
                                                                        .mapToInt(x -> x.intValue()).max().orElse(0)
                                                                        + 1,
                                                        tiles.size() * coms.size());
                                        var jobOrderingChromossome = IntegerChromosome.of(
                                                        0,
                                                        jobs.size(),
                                                        jobs.size());
                                        return Genotype.of(taskMappingAndSchedulingChromossome,
                                                        channelReservationsChromossome, jobOrderingChromossome);
                                },
                                gt -> {
                                        Map<String, String> taskMapping = IntStream
                                                        .range(0, gt.get(0).length()).boxed()
                                                        .collect(Collectors.toMap(
                                                                        tasks::get,
                                                                        idx -> mems.get(gt.get(0).get(idx).allele())));
                                        Map<String, String> taskScheduling = IntStream
                                                        .range(0, gt.get(0).length()).boxed()
                                                        .collect(Collectors.toMap(
                                                                        tasks::get,
                                                                        idx -> scheds.get(
                                                                                        gt.get(0).get(idx).allele())));
                                        Map<String, String> bufferMapping = IntStream.range(0, gt.get(0).length())
                                                        .boxed()
                                                        .collect(Collectors.toMap(
                                                                        bufs::get,
                                                                        idx -> mems.get(gt.get(0).get(idx).allele())));
                                        Map<String, Map<String, Integer>> channelReservations = IntStream
                                                        .range(0, tiles.size())
                                                        .boxed()
                                                        .collect(Collectors.toMap(
                                                                        bufI -> tiles.get(bufI),
                                                                        bufI -> IntStream.range(0, coms.size())
                                                                                        .boxed()
                                                                                        .collect(Collectors.toMap(
                                                                                                        ceI -> coms.get(ceI),
                                                                                                        ceI -> gt.get(1).get(
                                                                                                                        coms.size() * bufI
                                                                                                                                        + ceI)
                                                                                                                        .allele()))));
                                        var jobOrderings = gt.get(2);
                                        // System.out.println(taskScheduling.toString());
                                        // System.out.println(jobs.toString());
                                        // System.out.println(jobOrderings.stream().map(x -> x.allele())
                                        // .map(x -> x.toString()).reduce((a, b) -> a + ", " + b));
                                        Map<String, List<String>> superLoopSchedules = scheds.stream()
                                                        .collect(Collectors.toMap(
                                                                        k -> k,
                                                                        k -> IntStream.range(0, jobOrderings.length())
                                                                                        .boxed()
                                                                                        .filter(idx -> taskScheduling
                                                                                                        .get(jobs.get(idx)
                                                                                                                        .process())
                                                                                                        .equals(k))
                                                                                        .sorted((idxa, idxb) -> jobOrderings
                                                                                                        .get(idxa)
                                                                                                        .allele()
                                                                                                        .compareTo(jobOrderings
                                                                                                                        .get(idxb)
                                                                                                                        .allele()))
                                                                                        .map(idx -> jobs.get(idx)
                                                                                                        .process())
                                                                                        .collect(Collectors.toList())));
                                        // System.out.println(superLoopSchedules.toString());
                                        return new AperiodicAsynchronousDataflowToPartitionedTiledMulticore(
                                                        decisionModel.aperiodicAsynchronousDataflows(),
                                                        decisionModel.partitionedTiledMulticore(),
                                                        decisionModel.instrumentedComputationTimes(),
                                                        decisionModel.InstrumentedMemoryRequirements(),
                                                        taskScheduling,
                                                        taskMapping,
                                                        bufferMapping,
                                                        superLoopSchedules,
                                                        channelReservations);
                                },
                                m -> {
                                        var model = (AperiodicAsynchronousDataflowToPartitionedTiledMulticore) m;
                                        var taskMappingChromossome = IntegerChromosome
                                                        .of(model.processesToMemoryMapping().entrySet().stream()
                                                                        .map(e -> IntegerGene.of(
                                                                                        mems.indexOf(e.getValue()), 0,
                                                                                        mems.size()))
                                                                        .collect(ISeq.toISeq()));
                                        var maxReservations = decisionModel.partitionedTiledMulticore().hardware()
                                                        .communicationElementsMaxChannels().values()
                                                        .stream()
                                                        .mapToInt(x -> x.intValue()).max().orElse(0)
                                                        + 1;
                                        IntegerChromosome channelReservationsChromossome = IntegerChromosome
                                                        .of(model.partitionedTiledMulticore()
                                                                        .hardware()
                                                                        .processors()
                                                                        .stream()
                                                                        .flatMap(pe -> model
                                                                                        .partitionedTiledMulticore()
                                                                                        .hardware()
                                                                                        .communicationElements()
                                                                                        .stream()
                                                                                        .map(ce -> IntegerGene.of(
                                                                                                        decisionModel.processingElementsToRoutersReservations()
                                                                                                                        .getOrDefault(pe,
                                                                                                                                        Map.of())
                                                                                                                        .getOrDefault(ce,
                                                                                                                                        0),
                                                                                                        0,
                                                                                                        maxReservations)))
                                                                        .collect(ISeq.toISeq()));
                                        var orderings = new int[jobs.size()];
                                        Arrays.fill(orderings, -1);
                                        for (var sched : decisionModel.superLoopSchedules().keySet()) {
                                                var looplist = decisionModel.superLoopSchedules().get(sched);
                                                IntStream.range(0, looplist.size()).forEach(entryI -> {
                                                        var entry = looplist.get(entryI);
                                                        // this gets the first instance of a process that is still not
                                                        // scheduled and assigns it to the ordering encoding
                                                        IntStream.range(0, jobs.size())
                                                                        .filter(j -> model
                                                                                        .processesToRuntimeScheduling()
                                                                                        .get(entry).equals(sched)
                                                                                        && orderings[j] == -1
                                                                                        && jobs.get(j).process()
                                                                                                        .equals(entry))
                                                                        .boxed()
                                                                        .min((a, b) -> (int) jobs.get(a).instance()
                                                                                        - (int) jobs.get(b).instance())
                                                                        .ifPresent(j -> orderings[j] = entryI);
                                                });
                                        }
                                        var jobOrderingChromossome = IntegerChromosome.of(IntStream
                                                        .range(0, jobs.size())
                                                        .mapToObj(j -> IntegerGene.of(orderings[j], 0, jobs.size()))
                                                        .collect(ISeq.toISeq()));
                                        return Genotype.of(taskMappingChromossome, channelReservationsChromossome,
                                                        jobOrderingChromossome);
                                });
        }

        default Stream<ExplorationSolution> exploreAADPTM(
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel,
                        Set<ExplorationSolution> previousSolutions, Explorer.Configuration configuration) {
                var codec = ofDecisionModel(decisionModel);
                var orderingConstraint = new AperiodicAsynchronousDataflowJobOrderingConstraint<Vec<double[]>>(
                                decisionModel);
                var executableConstraint = new FeasibleMappingConstraint<Vec<double[]>>(decisionModel);
                var allConstraints = new MultiConstraint<>(
                                new CommunicationConstraint<>(decisionModel),
                                orderingConstraint,
                                executableConstraint
                // RetryConstraint.of(codec, this::mappingIsFeasible))
                );
                var jobs = decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.jobsOfProcesses().stream()).toList();
                var jobGraph = orderingConstraint.getJobGraph();
                SimpleDirectedGraph<Integer, DefaultEdge> jobIdxGraph = new SimpleDirectedGraph<>(DefaultEdge.class);
                jobGraph.edgeSet().forEach(e -> {
                        var src = jobs.indexOf(jobGraph.getEdgeSource(e));
                        var dst = jobs.indexOf(jobGraph.getEdgeTarget(e));
                        jobIdxGraph.addVertex(src);
                        jobIdxGraph.addVertex(dst);
                        jobIdxGraph.addEdge(src, dst);
                });
                var engine = Engine
                                .builder(g -> evaluateAADPTM(g, jobs, jobIdxGraph, configuration),
                                                allConstraints.constrain(codec))
                                .offspringSelector(new TournamentSelector<>(5))
                                .survivorsSelector(UFTournamentSelector.ofVec())
                                .constraint(allConstraints)
                                .alterers(
                                                new UniformCrossover<>(0.2, 0.25),
                                                new Mutator<>(0.2))
                                .minimizing()
                                .build();
                var solStream = engine
                                .stream(previousSolutions.stream().filter(s -> s
                                                .solved() instanceof AperiodicAsynchronousDataflowToPartitionedTiledMulticore)
                                                .map(s -> codec.encode(
                                                                (AperiodicAsynchronousDataflowToPartitionedTiledMulticore) s
                                                                                .solved()))
                                                .collect(Collectors.toList()));
                var timedSolStream = configuration.improvementTimeOutInSecs > 0L
                                ? solStream
                                                .limit(Limits.byExecutionTime(Duration.ofSeconds(
                                                                configuration.improvementTimeOutInSecs)))
                                : solStream;
                var limitedImprovementStream = configuration.improvementIterations > 0L
                                ? timedSolStream.limit(Limits.byFixedGeneration(configuration.improvementIterations))
                                : timedSolStream;
                return limitedImprovementStream
                                .map(sol -> {
                                        var decoded = codec.decode(sol.bestPhenotype().genotype());
                                        var solMap = new HashMap<String, Double>(sol.bestFitness().length());
                                        var bestFit = sol.bestFitness().data();
                                        var i = 0;
                                        if (configuration.targetObjectives.isEmpty()
                                                        || configuration.targetObjectives.contains("nUsedPEs")) {
                                                solMap.put("nUsedPEs", bestFit[0]);
                                                i = 1;
                                        }
                                        for (var app : decoded.aperiodicAsynchronousDataflows()) {
                                                for (var actor : app.processes()) {
                                                        if (configuration.targetObjectives.isEmpty()
                                                                        || configuration.targetObjectives
                                                                                        .contains("invThroughput(%s)"
                                                                                                        .formatted(actor))) {
                                                                solMap.put("invThroughput(%s)".formatted(actor),
                                                                                bestFit[i]);
                                                                app.processMinimumThroughput().put(actor,
                                                                                1.0 / bestFit[i]);
                                                                i += 1;
                                                        }
                                                }
                                        }
                                        return new ExplorationSolution(solMap, decoded);
                                });
        }

        private Vec<double[]> evaluateAADPTM(
                        final AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel,
                        final List<AperiodicAsynchronousDataflow.Job> jobs,
                        final Graph<Integer, DefaultEdge> jobIdxGraph,
                        Explorer.Configuration configuration) {
                // first, get the number of used PEs
                Function<Integer, String> taskName = (i) -> decisionModel.aperiodicAsynchronousDataflows().stream()
                                .flatMap(app -> app.processes().stream()).skip(i <= 0 ? 0 : i - 1)
                                .findFirst().get();
                var nUsedPEs = decisionModel.processesToRuntimeScheduling().values().stream().distinct().count();
                // get durations for each process
                var scheds = decisionModel.partitionedTiledMulticore().runtimes().runtimes().stream().toList();
                var mappings = jobs.stream()
                                .mapToInt(item -> scheds.indexOf(decisionModel.processesToRuntimeScheduling()
                                                .get(item.process())))
                                .toArray();
                var execTimes = jobs.stream().mapToDouble(j -> recomputeExecutionTime(decisionModel, j))
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
                        for (int dstI = 0; dstI < jobs.size(); dstI++) {
                                edgeWeights[srcI][dstI] = recomputeExecutionTransferTime(decisionModel, jobs.get(srcI),
                                                jobs.get(dstI));
                        }
                }
                var cycleLengths = recomputeMaximumCycles(jobIdxGraph,
                                jobs.stream().mapToInt(
                                                job -> scheds.indexOf(decisionModel.processesToRuntimeScheduling()
                                                                .get(job.process())))
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
                // + 1 is because the obejctives include number of used PEs
                var objs = new double[configuration.targetObjectives.isEmpty() ? invThroughputs.size() + 1
                                : configuration.targetObjectives.size()];
                var i = 0;
                if (configuration.targetObjectives.isEmpty() || configuration.targetObjectives.contains("nUsedPEs")) {
                        objs[i] = (double) nUsedPEs;
                        i = 1;
                }
                for (int j = 0; j < invThroughputs.size(); j++) {
                        if (configuration.targetObjectives.isEmpty()
                                        || configuration.targetObjectives
                                                        .contains("invThroughput(%s)".formatted(taskName.apply(j)))) {
                                objs[i] = invThroughputs.get(j);
                                i++;
                        }
                }
                return Vec.of(objs);
        }

        // default boolean mappingIsFeasible(
        // AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel) {
        // return
        // decisionModel.processesToRuntimeScheduling().entrySet().stream().allMatch(e
        // -> {
        // var process = e.getKey();
        // var hostSched = e.getValue();
        // var hostPe =
        // decisionModel.partitionedTiledMulticore().runtimes().runtimeHost()
        // .get(hostSched);
        // return
        // decisionModel.instrumentedComputationTimes().worstExecutionTimes().get(process)
        // .containsKey(hostPe);
        // });
        // }

        default double recomputeExecutionTime(
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel,
                        AperiodicAsynchronousDataflow.Job job) {
                var sched = decisionModel.processesToRuntimeScheduling().get(job.process());
                var pe = decisionModel.partitionedTiledMulticore().runtimes().runtimeHost().get(sched);
                var t = decisionModel.instrumentedComputationTimes().worstExecutionTimes()
                                .get(job.process()).getOrDefault(pe, Long.MAX_VALUE).doubleValue()
                                / decisionModel.instrumentedComputationTimes().scaleFactor()
                                                .doubleValue();
                return t;

        }

        default double recomputeExecutionTransferTime(
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel,
                        AperiodicAsynchronousDataflow.Job src,
                        AperiodicAsynchronousDataflow.Job dst) {
                var totalTime = 0.0;
                for (var srcPE : decisionModel.partitionedTiledMulticore().hardware().processors()) {
                        var srcSched = decisionModel.partitionedTiledMulticore().runtimes().processorAffinities()
                                        .get(srcPE);
                        if (decisionModel.processesToRuntimeScheduling().get(src.process()).equals(srcSched)) { // a
                                // task
                                // is
                                // mapped
                                // in PE i
                                for (var dstPE : decisionModel.partitionedTiledMulticore().hardware().processors()) {
                                        var dstSched = decisionModel.partitionedTiledMulticore().runtimes()
                                                        .processorAffinities()
                                                        .get(dstPE);
                                        if (decisionModel.processesToRuntimeScheduling().get(dst.process())
                                                        .equals(dstSched) && srcPE != dstPE) { // a task
                                                double singleBottleNeckBW = decisionModel
                                                                .partitionedTiledMulticore()
                                                                .hardware()
                                                                .preComputedPaths()
                                                                .getOrDefault(srcPE, Map.of())
                                                                .getOrDefault(dstPE, List.of())
                                                                .stream()
                                                                .mapToDouble(ce -> decisionModel
                                                                                .partitionedTiledMulticore()
                                                                                .hardware()
                                                                                .communicationElementsBitPerSecPerChannel()
                                                                                .get(ce) *
                                                                                Math.max(decisionModel
                                                                                                .processingElementsToRoutersReservations()
                                                                                                .get(srcPE)
                                                                                                .get(ce)
                                                                                                .doubleValue(), 1))
                                                                .min()
                                                                .orElse(1.0);
                                                totalTime += decisionModel
                                                                .partitionedTiledMulticore()
                                                                .hardware()
                                                                .preComputedPaths()
                                                                .getOrDefault(srcPE, Map.of())
                                                                .getOrDefault(dstPE, List.of())
                                                                .size()
                                                                * (decisionModel.aperiodicAsynchronousDataflows()
                                                                                .stream()
                                                                                .mapToDouble(app -> app
                                                                                                .processPutInBufferInBits()
                                                                                                .containsKey(src.process())
                                                                                                                ? app.processPutInBufferInBits()
                                                                                                                                .get(src.process())
                                                                                                                                .values()
                                                                                                                                .stream()
                                                                                                                                .mapToDouble(l -> l)
                                                                                                                                .sum()
                                                                                                                : 0.0)
                                                                                .sum()
                                                                                / singleBottleNeckBW);
                                                totalTime += decisionModel
                                                                .partitionedTiledMulticore()
                                                                .hardware()
                                                                .preComputedPaths()
                                                                .getOrDefault(srcPE, Map.of())
                                                                .getOrDefault(dstPE, List.of())
                                                                .size()
                                                                * (decisionModel.aperiodicAsynchronousDataflows()
                                                                                .stream()
                                                                                .mapToDouble(app -> app
                                                                                                .processGetFromBufferInBits()
                                                                                                .containsKey(dst.process())
                                                                                                                ? app.processGetFromBufferInBits()
                                                                                                                                .get(dst.process())
                                                                                                                                .values()
                                                                                                                                .stream()
                                                                                                                                .mapToDouble(l -> l)
                                                                                                                                .sum()
                                                                                                                : 0.0)
                                                                                .sum()
                                                                                / singleBottleNeckBW);
                                        }
                                        // is
                                        // mapped
                                        // in PE i
                                }
                        }
                }
                return totalTime;
        }

        class CommunicationConstraint<T extends Comparable<? super T>> implements
                        Constraint<IntegerGene, T> {

                private final List<String> processors;
                private final List<String> memories;
                private final List<String> comms;
                private final List<String> tasks;
                public AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel;

                public CommunicationConstraint(
                                AperiodicAsynchronousDataflowToPartitionedTiledMulticore decisionModel) {
                        this.decisionModel = decisionModel;
                        processors = decisionModel.partitionedTiledMulticore().hardware().processors()
                                        .stream().toList();
                        memories = decisionModel.partitionedTiledMulticore().hardware().memories().stream()
                                        .toList();
                        comms = decisionModel.partitionedTiledMulticore().hardware().communicationElements().stream()
                                        .toList();
                        tasks = decisionModel.aperiodicAsynchronousDataflows().stream()
                                        .flatMap(x -> x.processes().stream())
                                        .collect(Collectors.toList());
                }

                @Override
                public boolean test(Phenotype<IntegerGene, T> individual) {
                        var taskMapping = individual.genotype().get(0);
                        var taskScheduling = individual.genotype().get(0);
                        // var bufferMapping = individual.genotype().get(0);
                        var reservations = individual.genotype().get(1);
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
                                                                                .partitionedTiledMulticore()
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
                                                }
                                        }
                                }
                        }
                        return true;
                }

                @Override
                public Phenotype<IntegerGene, T> repair(Phenotype<IntegerGene, T> individual,
                                long generation) {
                        var taskMapping = individual.genotype().get(0);
                        var taskScheduling = individual.genotype().get(0);
                        // var bufferMapping = individual.genotype().get(0);
                        var reservations = individual.genotype().get(1);
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
                                                                for (var ce : decisionModel
                                                                                .partitionedTiledMulticore()
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
                                                }
                                        }
                                }
                        }
                        return Phenotype.of(
                                        Genotype.of(
                                                        taskMapping,
                                                        IntegerChromosome.of(IntStream.range(0, reservations.length())
                                                                        .mapToObj(idx -> changeReservations.get(idx)
                                                                                        ? IntegerGene.of(Math.max(
                                                                                                        reservations
                                                                                                                        .get(idx)
                                                                                                                        .allele(),
                                                                                                        1),
                                                                                                        0,
                                                                                                        reservations
                                                                                                                        .get(idx)
                                                                                                                        .max())
                                                                                        : reservations.get(idx))
                                                                        .collect(ISeq.toISeq())),
                                                        individual.genotype().get(2)),
                                        generation);
                }
        }

}
