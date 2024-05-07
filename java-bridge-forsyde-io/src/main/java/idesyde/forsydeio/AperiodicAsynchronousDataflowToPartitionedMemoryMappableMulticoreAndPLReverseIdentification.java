package idesyde.forsydeio;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL;
import idesyde.core.AutoRegister;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.ReverseIdentificationRule;

import java.util.Set;
import java.util.stream.Collectors;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;

@AutoRegister(ForSyDeIOModule.class)
public class AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPLReverseIdentification
                implements ReverseIdentificationRule {
        private Set<DesignModel> innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL(
                        Set<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL> solvedModels,
                        Set<ForSyDeIODesignModel> designModels) {
                return solvedModels.stream().map(model -> {
                        var reversedSystemGraph = new SystemGraph();
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
                        model.superLoopSchedules().forEach((sched, looplist) -> {
                                var schedVertex = reversedSystemGraph.newVertex(sched);
                                var scheduler = ForSyDeHierarchy.SuperLoopRuntime
                                                .enforce(reversedSystemGraph, schedVertex);
                                scheduler.superLoopEntries(looplist);
                        });
                        model.aperiodicAsynchronousDataflows()
                                        .forEach(app -> {
                                                app.processMinimumThroughput().entrySet().forEach(e -> {
                                                        var scale = 1.0;
                                                        while (Math.ceil(e.getValue() * scale)
                                                                        - (e.getValue() * scale) > 0.0001) {
                                                                scale *= 10.0;
                                                        }
                                                        for (var actor : app.processes()) {
                                                                var process = reversedSystemGraph.newVertex(actor);
                                                                var behaviour = ForSyDeHierarchy.AnalyzedBehavior
                                                                                .enforce(reversedSystemGraph, process);
                                                                if (behaviour.throughputInSecsDenominator() == null
                                                                                || behaviour.throughputInSecsNumerator() == null) {
                                                                        behaviour.throughputInSecsDenominator(1L);
                                                                        behaviour.throughputInSecsNumerator(0L);
                                                                }
                                                                if ((double) behaviour.throughputInSecsNumerator()
                                                                                / (double) behaviour
                                                                                                .throughputInSecsDenominator() >= 1.0
                                                                                                                / e.getValue()
                                                                                || behaviour.throughputInSecsNumerator() == 0) {
                                                                        behaviour.throughputInSecsDenominator(
                                                                                        (long) (e.getValue() * scale));
                                                                        behaviour.throughputInSecsNumerator(
                                                                                        (long) scale);
                                                                }
                                                        }
                                                });
                                                app.buffers().forEach(channel -> {
                                                        var channelVec = reversedSystemGraph.newVertex(channel);
                                                        var bbuf = ForSyDeHierarchy.BoundedBufferLike
                                                                        .enforce(reversedSystemGraph, channelVec);
                                                        var maxElems = app.bufferTokenSizeInbits().get(channel) > 0
                                                                        ? app.bufferMaxSizeInBits().get(channel)
                                                                                        / app.bufferTokenSizeInbits()
                                                                                                        .get(channel)
                                                                        : 1;
                                                        bbuf.maxElements((int) maxElems);
                                                        bbuf.elementSizeInBits(
                                                                        app.bufferTokenSizeInbits().get(channel));
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
                return innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL(
                                filteredSolved,
                                filteredDesign);
        }
}
