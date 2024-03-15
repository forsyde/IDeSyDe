package idesyde.forsydeio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import idesyde.common.InstrumentedComputationTimes;
import idesyde.core.*;

@AutoRegister(ForSyDeIOModule.class)
class InstrumentedComputationTimesIRule implements IdentificationRule {
    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels,
            Set<? extends DecisionModel> decisionModels) {
        var identified = new HashSet<InstrumentedComputationTimes>();
        var errors = new HashSet<String>();
        var model = new SystemGraph();
        for (var dm : designModels) {
            if (dm instanceof ForSyDeIODesignModel m) {
                model.mergeInPlace(m.systemGraph());
            }
        }
        var processes = new HashSet<String>();
        var processing_elements = new HashSet<String>();
        var best_execution_times = new HashMap<String, Map<String, Long>>();
        var average_execution_times = new HashMap<String, Map<String, Long>>();
        var worst_execution_times = new HashMap<String, Map<String, Long>>();
        var scale_factor = model.vertexSet()
                .stream()
                .mapToLong(v -> ForSyDeHierarchy.GenericProcessingModule
                        .tryView(model, v)
                        .map(x -> x.operatingFrequencyInHertz())
                        .orElse(1L))
                .max()
                .orElse(1L);
        // alll executables of task are instrumented
        model
                .vertexSet()
                .forEach(task -> ForSyDeHierarchy.InstrumentedBehaviour
                        .tryView(model, task)
                        .ifPresent(instrumentedBehaviour -> {
                            var taskName = instrumentedBehaviour.getIdentifier();
                            processes.add(taskName);
                            best_execution_times.put(taskName, new HashMap<>());
                            average_execution_times.put(taskName, new HashMap<>());
                            worst_execution_times.put(taskName, new HashMap<>());
                            model
                                    .vertexSet()
                                    .forEach(proc -> ForSyDeHierarchy.InstrumentedProcessingModule
                                            .tryView(model, proc)
                                            .ifPresent(instrumentedProc -> {
                                                var peName = instrumentedProc
                                                        .getIdentifier();
                                                processing_elements.add(
                                                        peName);
                                                instrumentedBehaviour
                                                        .computationalRequirements()
                                                        .values()
                                                        .stream()
                                                        .flatMapToLong(needs -> instrumentedProc
                                                                .modalInstructionsPerCycle()
                                                                .values()
                                                                .stream()
                                                                .filter(ops -> ops
                                                                        .keySet()
                                                                        .containsAll(needs
                                                                                .keySet()))
                                                                .mapToLong(ops -> needs
                                                                        .entrySet()
                                                                        .stream()
                                                                        .mapToLong(needEntry -> 1L
                                                                                + Math.floorDiv(
                                                                                        Math.round(needEntry.getValue()
                                                                                                .doubleValue()
                                                                                                / ops.get(
                                                                                                        needEntry
                                                                                                                .getKey())
                                                                                                        .doubleValue()
                                                                                                * (double) scale_factor),
                                                                                        instrumentedProc
                                                                                                .operatingFrequencyInHertz()))
                                                                        .sum()))
                                                        .max()
                                                        .ifPresent(execTime -> {
                                                            best_execution_times
                                                                    .get(taskName)
                                                                    .put(peName, execTime);
                                                            average_execution_times
                                                                    .get(taskName)
                                                                    .put(peName, execTime);
                                                            worst_execution_times
                                                                    .get(taskName)
                                                                    .put(peName, execTime);
                                                        });
                                            }));
                        }));
        identified.add(
                new InstrumentedComputationTimes(
                        average_execution_times,
                        best_execution_times,
                        processes,
                        processing_elements,
                        scale_factor,
                        worst_execution_times));
        return new IdentificationResult(
                identified, errors);
    }

}
