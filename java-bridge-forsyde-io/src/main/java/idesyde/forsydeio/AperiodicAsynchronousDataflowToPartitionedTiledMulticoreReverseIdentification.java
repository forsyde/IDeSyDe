package idesyde.forsydeio;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.AutoRegister;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.ReverseIdentificationRule;

import java.util.Set;
import java.util.stream.Collectors;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
@AutoRegister(ForSyDeIOModule.class)
public class AperiodicAsynchronousDataflowToPartitionedTiledMulticoreReverseIdentification
                implements ReverseIdentificationRule {

        private Set<DesignModel> innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedTiledMulticore(
                        Set<AperiodicAsynchronousDataflowToPartitionedTiledMulticore> solvedModels,
                        Set<ForSyDeIODesignModel> designModels) {
                return solvedModels.stream().map(model -> {
                        SystemGraph reversedSystemGraph = new SystemGraph();
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
                                                app.processes().forEach(process -> {
                                                        var th = app.processMinimumThroughput().get(process);
                                                        var processVertex = reversedSystemGraph.newVertex(process);
                                                        var behaviour = ForSyDeHierarchy.AnalyzedBehavior
                                                                        .enforce(reversedSystemGraph, processVertex);
                                                        // var scale = 1.0;
                                                        // while (Math.ceil(th * scale) - (th * scale) > 0.0001) {
                                                        // scale *= 10.0;
                                                        // }
                                                        behaviour.setThroughputInSecsDenominator(Math.round(th));
                                                        behaviour.setThroughputInSecsNumerator(1L);
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
                                .flatMap(x -> DecisionModel.cast(x, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class).stream())
                                .collect(Collectors.toSet());
                var filteredDesign = u.stream()
                                .flatMap(x -> ForSyDeIODesignModel.tryFrom(x).stream())
                                .collect(Collectors.toSet());
                return innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedTiledMulticore(filteredSolved,
                                filteredDesign);
        }
}
