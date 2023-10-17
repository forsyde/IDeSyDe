package idesyde.forsydeio;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.ReverseIdentificationRule;

import java.util.Set;
import java.util.stream.Collectors;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;

public class AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreReverseIdentification
        implements ReverseIdentificationRule {

    private Set<DesignModel> innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore(
            Set<AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore> solvedModels,
            Set<ForSyDeIODesignModel> designModels) {
        return solvedModels.stream().map(model -> {
            var reversedSystemGraph = new SystemGraph();
            model.processesToMemoryMapping().forEach((process, mem) -> {
                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(process).stream()).findAny()
                        .ifPresent(procVertex -> {
                            designModels.stream().flatMap(m -> m.systemGraph().queryVertex(mem).stream()).findAny()
                                    .ifPresent(memVertex -> {
                                        reversedSystemGraph.addVertex(procVertex);
                                        reversedSystemGraph.addVertex(memVertex);
                                        var memMapped = ForSyDeHierarchy.MemoryMapped.enforce(reversedSystemGraph,
                                                procVertex);
                                        memMapped.mappingHost(ForSyDeHierarchy.GenericMemoryModule
                                                .enforce(reversedSystemGraph, memVertex));
                                        ForSyDeHierarchy.GreyBox.enforce(reversedSystemGraph, memVertex)
                                                .addContained(ForSyDeHierarchy.Visualizable.enforce(memMapped));
                                    });
                        });
            });
            model.bufferToMemoryMappings().forEach((buf, mem) -> {
                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(buf).stream()).findAny()
                        .ifPresent(procVertex -> {
                            designModels.stream().flatMap(m -> m.systemGraph().queryVertex(mem).stream()).findAny()
                                    .ifPresent(memVertex -> {
                                        reversedSystemGraph.addVertex(procVertex);
                                        reversedSystemGraph.addVertex(memVertex);
                                        var memMapped = ForSyDeHierarchy.MemoryMapped.enforce(reversedSystemGraph,
                                                procVertex);
                                        memMapped.mappingHost(ForSyDeHierarchy.GenericMemoryModule
                                                .enforce(reversedSystemGraph, memVertex));
                                        ForSyDeHierarchy.GreyBox.enforce(reversedSystemGraph, memVertex)
                                                .addContained(ForSyDeHierarchy.Visualizable.enforce(memMapped));
                                    });
                        });
            });
            model.processesToRuntimeScheduling().forEach((process, sched) -> {
                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(process).stream()).findAny()
                        .ifPresent(procVertex -> {
                            designModels.stream().flatMap(m -> m.systemGraph().queryVertex(sched).stream()).findAny()
                                    .ifPresent(schedVertex -> {
                                        reversedSystemGraph.addVertex(procVertex);
                                        reversedSystemGraph.addVertex(schedVertex);
                                        var scheduled = ForSyDeHierarchy.Scheduled.enforce(reversedSystemGraph,
                                                procVertex);
                                        scheduled.runtimeHost(ForSyDeHierarchy.AbstractRuntime
                                                .enforce(reversedSystemGraph, schedVertex));
                                        ForSyDeHierarchy.GreyBox.enforce(reversedSystemGraph, schedVertex)
                                                .addContained(ForSyDeHierarchy.Visualizable.enforce(scheduled));
                                    });
                        });
            });
            model.superLoopSchedules().forEach((sched, looplist) -> {
                designModels.stream().flatMap(m -> m.systemGraph().queryVertex(sched).stream()).findAny()
                        .ifPresent(schedVertex -> {
                            var scheduler = ForSyDeHierarchy.SuperLoopRuntime.enforce(reversedSystemGraph, schedVertex);
                            scheduler.superLoopEntries(looplist);
                        });
            });
            model.aperiodicAsynchronousDataflows()
                    .forEach(app -> app.processMinimumThroughput().entrySet().forEach(e -> {
                        var process = reversedSystemGraph.queryVertex(e.getKey())
                                .orElse(reversedSystemGraph.newVertex(e.getKey()));
                        var behaviour = ForSyDeHierarchy.AnalyzedBehavior
                                .enforce(reversedSystemGraph, process);
                        var scale = 1.0;
                        while (Math.ceil(e.getValue() * scale)
                                - (e.getValue() * scale) > 0.0001) {
                            scale *= 10.0;
                        }
                        behaviour.setThroughputInSecsDenominator((long) (e.getValue() * scale));
                        behaviour.setThroughputInSecsNumerator((long) scale);
                    }));
            return new ForSyDeIODesignModel(reversedSystemGraph);
        }).collect(Collectors.toSet());
    }

    @Override
    public Set<DesignModel> apply(Set<? extends DecisionModel> t, Set<? extends DesignModel> u) {
        var filteredSolved = t.stream()
                .filter(x -> x instanceof AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore)
                .map(x -> (AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) x)
                .collect(Collectors.toSet());
        var filteredDesign = u.stream().filter(x -> x instanceof ForSyDeIODesignModel)
                .map(x -> (ForSyDeIODesignModel) x).collect(Collectors.toSet());
        return innerReverseIdentifyAperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore(filteredSolved,
                filteredDesign);
    }
}
