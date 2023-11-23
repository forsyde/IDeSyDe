package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflow;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import idesyde.metaheuristics.constraints.AperiodicAsynchronousDataflowJobOrderingConstraint;
import idesyde.metaheuristics.constraints.FeasibleMappingConstraint;
import idesyde.metaheuristics.constraints.MemoryMappableCommunicationConstraint;
import idesyde.metaheuristics.constraints.MultiConstraint;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.InvertibleCodec;
import io.jenetics.engine.Limits;
import io.jenetics.engine.RetryConstraint;
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

public interface CanExploreAADPMMMWithJenetics extends AperiodicAsynchronousDataflowMethods {

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
                    var taskMappingChromossome = IntegerChromosome.of(0,
                            decisionModel.partitionedMemMappableMulticore().hardware()
                                    .storageElems().size(),
                            procs.size());
                    var taskSchedulingChromossome = IntegerChromosome.of(0,
                            decisionModel.partitionedMemMappableMulticore().runtimes()
                                    .runtimes().size(),
                            procs.size());
                    var bufferMappingChromossome = IntegerChromosome.of(0,
                            decisionModel.partitionedMemMappableMulticore().hardware()
                                    .storageElems().size(),
                            bufs.size());
                    var channelReservationsChromossome = IntegerChromosome.of(0,
                            decisionModel.partitionedMemMappableMulticore().hardware()
                                    .communicationElementsMaxChannels().values()
                                    .stream()
                                    .mapToInt(x -> x).max().orElse(0),
                            decisionModel.partitionedMemMappableMulticore().hardware()
                                    .processingElems().size()
                                    * decisionModel.partitionedMemMappableMulticore()
                                    .hardware().communicationElems()
                                    .size());
                    var jobOrderingChromossome = IntegerChromosome.of(
                            0,
                            jobs.size(),
                            jobs.size());
                    return Genotype.of(taskMappingChromossome, taskSchedulingChromossome,
                            bufferMappingChromossome,
                            channelReservationsChromossome, jobOrderingChromossome);
                },
                gt -> {
                    var taskMapping = IntStream.range(0, gt.get(0).length()).boxed()
                            .collect(Collectors.toMap(
                                    procs::get,
                                    idx -> mems.get(gt.get(0).get(idx).allele())));
                    var taskScheduling = IntStream.range(0, gt.get(1).length()).boxed()
                            .collect(Collectors.toMap(
                                    procs::get,
                                    idx -> scheds.get(
                                            gt.get(1).get(idx).allele())));
                    var bufferMapping = IntStream.range(0, gt.get(2).length()).boxed()
                            .collect(Collectors.toMap(
                                    bufs::get,
                                    idx -> mems.get(gt.get(2).get(idx).allele())));
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
                    var jobOrderings = gt.get(4);
                    var superLoopSchedules = scheds.stream().collect(Collectors.toMap(
                            k -> k,
                            k -> IntStream.range(0, jobOrderings.length()).boxed()
                                    .filter(idx -> taskScheduling
                                            .get(jobs.get(idx).process())
                                            .equals(k))
                                    .sorted((idxa, idxb) -> jobOrderings
                                            .get(idxa)
                                            .allele()
                                            .compareTo(jobOrderings
                                                    .get(idxb)
                                                    .allele()))
                                    .map(idx -> jobs.get(idx).process())
                                    .collect(Collectors.toList())));
                    return new AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore(
                            decisionModel.aperiodicAsynchronousDataflows(),
                            decisionModel.partitionedMemMappableMulticore(),
                            decisionModel.instrumentedComputationTimes(),
                            decisionModel.instrumentedMemoryRequirements(),
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
                                            mems.indexOf(e.getValue()), 0,
                                            mems.size()))
                                    .collect(ISeq.toISeq()));
                    var taskSchedulingChromossome = IntegerChromosome
                            .of(model.processesToRuntimeScheduling().entrySet().stream()
                                    .map(e -> IntegerGene.of(
                                            scheds.indexOf(e.getValue()), 0,
                                            scheds.size()))
                                    .collect(ISeq.toISeq()));
                    var bufferMappingChromossome = IntegerChromosome
                            .of(model.bufferToMemoryMappings().entrySet().stream()
                                    .map(e -> IntegerGene.of(
                                            mems.indexOf(e.getValue()), 0,
                                            mems.size()))
                                    .collect(ISeq.toISeq()));
                    var channelReservationsChromossome = IntegerChromosome
                            .of(model.partitionedMemMappableMulticore().hardware()
                                    .processingElems()
                                    .stream()
                                    .flatMap(pe -> model
                                            .partitionedMemMappableMulticore()
                                            .hardware()
                                            .communicationElems()
                                            .stream()
                                            .map(ce -> IntegerGene.of(
                                                    decisionModel.processingElementsToRoutersReservations()
                                                            .getOrDefault(pe,
                                                                    Map.of())
                                                            .getOrDefault(ce,
                                                                    0),
                                                    0,
                                                    decisionModel.partitionedMemMappableMulticore()
                                                            .hardware()
                                                            .communicationElementsMaxChannels()
                                                            .getOrDefault(ce,
                                                                    0))))
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
                            .mapToObj(j -> IntegerGene.of(orderings[j], 0, jobs.size()))
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
        var orderingConstraint = new AperiodicAsynchronousDataflowJobOrderingConstraint<Vec<double[]>>(
                decisionModel);
        var executableConstraint = new FeasibleMappingConstraint<Vec<double[]>>(decisionModel);
        var allConstraints = new MultiConstraint<>(
                new MemoryMappableCommunicationConstraint<>(decisionModel),
                RetryConstraint.of(codec, this::mappingIsFeasible),
                orderingConstraint,
                executableConstraint
//                                                RetryConstraint.of(codec, this::mappingIsFeasible))
        );
        var jobs = decisionModel.aperiodicAsynchronousDataflows().stream().flatMap(app -> app.jobsOfProcesses().stream()).toList();
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
                .builder(g -> evaluateAADPMMM(g, jobs, jobIdxGraph, configuration), allConstraints.constrain(codec))
                .populationSize(decisionModel.partitionedMemMappableMulticore().runtimes().runtimes()
                        .size() * decisionModel.aperiodicAsynchronousDataflows().size() * 5)
                .offspringSelector(new TournamentSelector<>(5))
                .survivorsSelector(UFTournamentSelector.ofVec())
                .constraint(allConstraints)
                .alterers(
                        new UniformCrossover<>(0.2, 0.25),
                        new Mutator<>(0.2)
                )
                .minimizing()
                .build();
        var solStream = engine
                .stream(previousSolutions.stream().filter(s -> s
                                .solved() instanceof AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore)
                        .map(s -> codec.encode(
                                (AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) s
                                        .solved()))
                        .collect(Collectors.toList()));
        // .limit(Limits.byGeneConvergence(0.000001, 0.999999));
        var timedSolStream = configuration.improvementTimeOutInSecs > 0L
                ? solStream
                .limit(Limits.byExecutionTime(Duration.ofSeconds(
                        configuration.improvementTimeOutInSecs)))
                : solStream;
        var limitedImprovementStream = configuration.improvementIterations > 0L
                ? timedSolStream.limit(Limits.byFixedGeneration(configuration.improvementIterations))
                : timedSolStream;
        // var limitedSolStream = configuration.maximumSolutions > 0L
        // ? decodedStream.limit(configuration.maximumSolutions)
        // : decodedStream;
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
                                    || (configuration.targetObjectives
                                    .contains("invThroughput(%s)"
                                            .formatted(actor)))) {
                                solMap.put("invThroughput(%s)".formatted(actor),
                                        bestFit[i]);
                                app.processMinimumThroughput().put(actor, 1.0 / bestFit[i]);
                                i += 1;
                            }
                        }
                    }
                    return new ExplorationSolution(solMap, decoded);
                });
    }

    private Vec<double[]> evaluateAADPMMM(
            final AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel,
            final List<AperiodicAsynchronousDataflow.Job> jobs,
            final Graph<Integer, DefaultEdge> jobIdxGraph,
            Explorer.Configuration configuration) {
        // first, get the number of used PEs
        var nUsedPEs = decisionModel.processesToRuntimeScheduling().values().stream().distinct().count();
        // get durations for each process
        Function<Integer, String> taskName = (i) -> decisionModel.aperiodicAsynchronousDataflows().stream()
                .sequential()
                .flatMap(app -> app.processes().stream().sequential()).skip(i <= 0 ? 0 : i - 1)
                .findFirst().get();
        var scheds = decisionModel.partitionedMemMappableMulticore().runtimes().runtimes().stream().toList();
        var mappings = IntStream.range(0, jobs.size()).map(
                        jobI -> scheds.indexOf(decisionModel.processesToRuntimeScheduling()
                                .get(jobs.get(jobI).process())))
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
                edgeWeights[srcI][dstI] = 0.0;
            }
        }
        var cycleLengths = recomputeMaximumCycles(jobIdxGraph,
                jobs.stream().mapToInt(job -> scheds.indexOf(decisionModel.processesToRuntimeScheduling()
                                .get(job.process())))
                        .toArray(),
                orderings, execTimes, edgeWeights);
        var invThroughputs = decisionModel.aperiodicAsynchronousDataflows().stream()
                .flatMap(app -> app.processes().stream())
                // add the throughputs to the decision model for the sake of later reference
                .map(actor -> {
                    var maxRepetitions = jobs.stream()
                            .filter(j -> j.process().equals(actor))
                            .mapToLong(AperiodicAsynchronousDataflow.Job::instance).max()
                            .orElse(1L);
                    return IntStream.range(0, jobs.size())
                            .filter(j -> jobs.get(j).process().equals(actor))
                            .mapToDouble(j -> cycleLengths[j] / maxRepetitions)
                            .max().orElse(0.0);
                })
                .collect(Collectors.toList());
        // + 1 is because the obejctives include number of used PEs
        // the many checks arise because of possible subsetting of optimisation goals
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

    default boolean mappingIsFeasible(
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel) {
        return decisionModel.partitionedMemMappableMulticore().hardware().storageSizes().entrySet().stream()
                .allMatch(memEntry -> {
                    var taskUsage = decisionModel.processesToRuntimeScheduling().entrySet().stream()
                            .map(taskEntry -> Map.entry(taskEntry.getKey(),
                                    decisionModel.partitionedMemMappableMulticore()
                                            .runtimes().runtimeHost()
                                            .get(taskEntry.getValue())))
                            .filter(taskEntry -> taskEntry.getValue()
                                    .equals(memEntry.getKey()))
                            .mapToLong(taskEntry -> decisionModel
                                    .instrumentedMemoryRequirements()
                                    .memoryRequirements()
                                    .getOrDefault(taskEntry.getKey(), Map.of())
                                    .getOrDefault(memEntry.getKey(), 0L))
                            .sum();
                    var channelUsage = decisionModel.bufferToMemoryMappings().entrySet().stream()
                            .filter(bufferEntry -> bufferEntry.getValue()
                                    .equals(memEntry.getKey()))
                            .mapToLong(bufferEntry -> decisionModel
                                    .instrumentedMemoryRequirements()
                                    .memoryRequirements()
                                    .getOrDefault(bufferEntry.getKey(), Map.of())
                                    .getOrDefault(memEntry.getKey(), 0L))
                            .sum();
                    return taskUsage + channelUsage <= memEntry.getValue();
                });
    }

    default double recomputeExecutionTime(
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel,
            AperiodicAsynchronousDataflow.Job job) {
        var sched = decisionModel.processesToRuntimeScheduling().get(job.process());
        var pe = decisionModel.partitionedMemMappableMulticore().runtimes().runtimeHost().get(sched);
        var totalTime = decisionModel.instrumentedComputationTimes().worstExecutionTimes()
                .get(job.process()).getOrDefault(pe, Long.MAX_VALUE).doubleValue()
                / decisionModel.instrumentedComputationTimes().scaleFactor()
                .doubleValue();
        for (var me : decisionModel.partitionedMemMappableMulticore().hardware()
                .storageElems()) {
            if (decisionModel.processesToMemoryMapping().get(job.process()).equals(me)) { // the
                // data
                // is
                // mapped to a ME
                // j
                double singleBottleNeckBW = decisionModel
                        .partitionedMemMappableMulticore()
                        .hardware()
                        .preComputedPaths()
                        .getOrDefault(pe, Map.of())
                        .getOrDefault(me, List.of())
                        .stream()
                        .mapToDouble(ce -> decisionModel
                                .partitionedMemMappableMulticore()
                                .hardware()
                                .communicationElementsBitPerSecPerChannel()
                                .get(ce) *
                                decisionModel
                                        .processingElementsToRoutersReservations()
                                        .get(pe)
                                        .get(ce)
                                        .doubleValue())
                        .min()
                        .orElse(1.0);
                // fetch time = total links * (memory reqs / bottleneck BW)
                totalTime += decisionModel
                        .partitionedMemMappableMulticore()
                        .hardware()
                        .preComputedPaths()
                        .getOrDefault(pe, Map.of())
                        .getOrDefault(me, List.of())
                        .size()
                        * (decisionModel.instrumentedMemoryRequirements()
                        .memoryRequirements().get(job.process())
                        .get(pe).doubleValue()
                        / singleBottleNeckBW);

            }
            totalTime += decisionModel.aperiodicAsynchronousDataflows().stream()
                    .flatMap(x -> x.buffers().stream())
                    .mapToDouble(buffer -> {
                        var ioTime = 0.0;
                        if (decisionModel.aperiodicAsynchronousDataflows()
                                .stream().anyMatch(app -> app
                                        .processGetFromBufferInBits()
                                        .getOrDefault(job
                                                        .process(),
                                                Map
                                                        .of())
                                        .containsKey(buffer))
                                && decisionModel.bufferToMemoryMappings()
                                .get(buffer)
                                .equals(me)) { // task
                            // reads
                            // from
                            // buffer
                            var singleBottleNeckBW = decisionModel
                                    .partitionedMemMappableMulticore()
                                    .hardware()
                                    .preComputedPaths()
                                    .getOrDefault(pe, Map.of())
                                    .getOrDefault(me, List.of())
                                    .stream()
                                    .mapToDouble(ce -> decisionModel
                                            .partitionedMemMappableMulticore()
                                            .hardware()
                                            .communicationElementsBitPerSecPerChannel()
                                            .get(ce) *
                                            decisionModel
                                                    .processingElementsToRoutersReservations()
                                                    .get(pe)
                                                    .get(ce))
                                    .min()
                                    .orElse(1.0);
                            // read time = links * (total data transmitted /
                            // bottleneck bw)
                            ioTime += decisionModel
                                    .partitionedMemMappableMulticore()
                                    .hardware()
                                    .preComputedPaths()
                                    .getOrDefault(pe, Map.of())
                                    .getOrDefault(me, List.of())
                                    .size() * (

                                    decisionModel
                                            .aperiodicAsynchronousDataflows()
                                            .stream()
                                            .mapToLong(app -> app
                                                    .processGetFromBufferInBits()
                                                    .containsKey(job.process())
                                                    ? app.processGetFromBufferInBits()
                                                    .get(job.process())
                                                    .getOrDefault(buffer,
                                                            0L)
                                                    : 0L)
                                            .sum() / singleBottleNeckBW);
                        }
                        if (decisionModel.aperiodicAsynchronousDataflows()
                                .stream().anyMatch(app -> app
                                        .processPutInBufferInBits()
                                        .getOrDefault(job
                                                        .process(),
                                                Map
                                                        .of())
                                        .containsKey(buffer))
                                && decisionModel.bufferToMemoryMappings()
                                .get(buffer)
                                .equals(me)) { // task
                            // writes
                            // to
                            // buffer
                            var singleBottleNeckBW = decisionModel
                                    .partitionedMemMappableMulticore()
                                    .hardware()
                                    .preComputedPaths()
                                    .getOrDefault(pe, Map.of())
                                    .getOrDefault(me, List.of())
                                    .stream()
                                    .mapToDouble(ce -> decisionModel
                                            .partitionedMemMappableMulticore()
                                            .hardware()
                                            .communicationElementsBitPerSecPerChannel()
                                            .get(ce) *
                                            decisionModel
                                                    .processingElementsToRoutersReservations()
                                                    .get(pe)
                                                    .get(ce))
                                    .min()
                                    .orElse(1.0);
                            // write nack time = links * (total data
                            // transmitted /
                            // bottleneck bw)
                            ioTime += decisionModel
                                    .partitionedMemMappableMulticore()
                                    .hardware()
                                    .preComputedPaths()
                                    .getOrDefault(pe, Map.of())
                                    .getOrDefault(me, List.of())
                                    .size() * (

                                    decisionModel
                                            .aperiodicAsynchronousDataflows()
                                            .stream()
                                            .mapToDouble(app -> app
                                                    .processPutInBufferInBits()
                                                    .containsKey(job.process())
                                                    ? app.processPutInBufferInBits()
                                                    .get(job.process())
                                                    .getOrDefault(buffer,
                                                            0L)
                                                    : 0L)
                                            .sum() / singleBottleNeckBW);
                        }
                        return ioTime;
                    }).sum();
        }
        return totalTime;
    }

}
