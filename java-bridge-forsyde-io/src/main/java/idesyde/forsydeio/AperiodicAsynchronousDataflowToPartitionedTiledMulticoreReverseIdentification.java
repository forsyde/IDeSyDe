package idesyde.forsydeio;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.ReverseIdentificationRule;

import java.util.Set;
import java.util.stream.Collectors;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;

public class AperiodicAsynchronousDataflowToPartitionedTiledMulticoreReverseIdentification
                implements ReverseIdentificationRule {

        private Set<DesignModel> innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedTiledMulticore(
                        Set<AperiodicAsynchronousDataflowToPartitionedTiledMulticore> solvedModels,
                        Set<ForSyDeIODesignModel> designModels) {
                return solvedModels.stream().map(model -> {
                        var reversedSystemGraph = new SystemGraph();
                        model.processesToMemoryMapping().forEach((process, mem) -> {
                                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(process).stream())
                                                .findAny()
                                                .ifPresent(procVertex -> {
                                                        designModels.stream()
                                                                        .flatMap(m -> m.systemGraph().queryVertex(mem)
                                                                                        .stream())
                                                                        .findAny()
                                                                        .ifPresent(memVertex -> {
                                                                                reversedSystemGraph
                                                                                                .addVertex(procVertex);
                                                                                reversedSystemGraph
                                                                                                .addVertex(memVertex);
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
                                                });
                        });
                        model.bufferToMemoryMappings().forEach((buf, mem) -> {
                                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(buf).stream()).findAny()
                                                .ifPresent(procVertex -> {
                                                        designModels.stream()
                                                                        .flatMap(m -> m.systemGraph().queryVertex(mem)
                                                                                        .stream())
                                                                        .findAny()
                                                                        .ifPresent(memVertex -> {
                                                                                reversedSystemGraph
                                                                                                .addVertex(procVertex);
                                                                                reversedSystemGraph
                                                                                                .addVertex(memVertex);
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
                                                });
                        });
                        model.processesToRuntimeScheduling().forEach((process, sched) -> {
                                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(process).stream())
                                                .findAny()
                                                .ifPresent(procVertex -> {
                                                        designModels.stream()
                                                                        .flatMap(m -> m.systemGraph().queryVertex(sched)
                                                                                        .stream())
                                                                        .findAny()
                                                                        .ifPresent(schedVertex -> {
                                                                                reversedSystemGraph
                                                                                                .addVertex(procVertex);
                                                                                reversedSystemGraph
                                                                                                .addVertex(schedVertex);
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
                                                });
                        });
                        model.superLoopSchedules().forEach((sched, looplist) -> {
                                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(sched).stream())
                                                .findAny()
                                                .ifPresent(schedVertex -> {
                                                        var scheduler = ForSyDeHierarchy.SuperLoopRuntime
                                                                        .enforce(reversedSystemGraph, schedVertex);
                                                        scheduler.superLoopEntries(looplist);
                                                });
                        });
                        model.aperiodicAsynchronousDataflows()
                                        .forEach(app -> app.processes().forEach(process -> {
                                                var th = app.processMinimumThroughput().get(process);
                                                var processVertex = reversedSystemGraph.queryVertex(process)
                                                                .orElse(reversedSystemGraph.newVertex(process));
                                                var behaviour = ForSyDeHierarchy.AnalyzedBehavior
                                                                .enforce(reversedSystemGraph, processVertex);
                                                // var scale = 1.0;
                                                // while (Math.ceil(th * scale) - (th * scale) > 0.0001) {
                                                // scale *= 10.0;
                                                // }
                                                behaviour.setThroughputInSecsDenominator(Math.round(th));
                                                behaviour.setThroughputInSecsNumerator(1L);
                                        }));
                        return new ForSyDeIODesignModel(reversedSystemGraph);
                }).collect(Collectors.toSet());
        }

        @Override
        public Set<DesignModel> apply(Set<? extends DecisionModel> t, Set<? extends DesignModel> u) {
                var filteredSolved = t.stream()
                                .filter(x -> x instanceof AperiodicAsynchronousDataflowToPartitionedTiledMulticore)
                                .map(x -> (AperiodicAsynchronousDataflowToPartitionedTiledMulticore) x)
                                .collect(Collectors.toSet());
                var filteredDesign = u.stream().filter(x -> x instanceof ForSyDeIODesignModel)
                                .map(x -> (ForSyDeIODesignModel) x).collect(Collectors.toSet());
                return innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedTiledMulticore(filteredSolved,
                                filteredDesign);
        }
}
