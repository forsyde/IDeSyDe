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
                                        .forEach(app -> app.processes().forEach(process -> {
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
                                        }));
                        // mereg HAS to come here; otherwise the vertex that java has a pointer too will
                        // be reused.
                        for (var x : designModels) {
                                reversedSystemGraph.mergeInPlace(x.systemGraph());
                        }
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
