package idesyde.forsydeio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import forsyde.io.core.SystemGraph;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import idesyde.common.InstrumentedMemoryRequirements;
import idesyde.core.*;

@AutoRegister(ForSyDeIOModule.class)
public class ForSyDeIOSYAndSDFInstrumentedToMemReqIRule implements IdentificationRule {

    @Override
    public IdentificationResult apply(Set<? extends DesignModel> designModels,
            Set<? extends DecisionModel> decisionModels) {
        var model = new SystemGraph();
        for (var dm : designModels) {
            ForSyDeIODesignModel.tryFrom(dm).map(ForSyDeIODesignModel::systemGraph).ifPresent(model::mergeInPlace);
        }
        Set<String> processes = new HashSet<>();
        Set<String> channels = new HashSet<>();
        Map<String, Map<String, Long>> memMapping = new HashMap<>();
        for (var v : model.vertexSet()) {
            ForSyDeHierarchy.InstrumentedSoftwareBehaviour.tryView(model, v).ifPresent(ib -> {
                processes.add(ib.getIdentifier());
                for (var peV : model.vertexSet()) {
                    ForSyDeHierarchy.InstrumentedProcessingModule.tryView(model, peV).ifPresentOrElse(inspe -> {
                        if (!memMapping.containsKey(ib.getIdentifier())) {
                            memMapping.put(ib.getIdentifier(), new HashMap<>());
                        }
                        memMapping.get(ib.getIdentifier()).put(inspe.getIdentifier(),
                                ib.maxSizeInBits().entrySet().stream()
                                        .filter(e -> inspe.modalInstructionCategory().contains(e.getKey()))
                                        .mapToLong(Map.Entry::getValue).max()
                                        .orElse(0L));
                    }, () -> {
                        ForSyDeHierarchy.GenericProcessingModule.tryView(model, peV).ifPresent(pe -> {
                            if (!memMapping.containsKey(ib.getIdentifier())) {
                                memMapping.put(ib.getIdentifier(), new HashMap<>());
                            }
                            memMapping.get(ib.getIdentifier()).put(pe.getIdentifier(),
                                    ib.maxSizeInBits().values().stream().mapToLong(Long::longValue).max()
                                            .orElse(0L));
                        });
                    });
                }
            });
            ForSyDeHierarchy.InstrumentedDataType.tryView(model, v).ifPresent(idt -> {
                channels.add(idt.getIdentifier());
                for (var peV : model.vertexSet()) {
                    ForSyDeHierarchy.GenericProcessingModule.tryView(model, peV).ifPresent(pe -> {
                        if (!memMapping.containsKey(idt.getIdentifier())) {
                            memMapping.put(idt.getIdentifier(), new HashMap<>());
                        }
                        memMapping.get(idt.getIdentifier()).put(pe.getIdentifier(),
                                idt.maxSizeInBits().values().stream().mapToLong(x -> x).max().orElse(0L));
                    });
                }
            });
        }

        // accept if all SDF or SY behaviours are instrumented
        // if (processes.isEmpty() || channels.isEmpty() || memMapping.isEmpty()) {
        // return new IdentificationResult(Set.of(), Set.of(
        // "ForSyDeIOSYAndSDFInstrumentedToMemReqIRule: no instrumented processes or
        // channels found"));
        // }
        // var allSYOk = model.vertexSet().stream().filter(v ->
        // ForSyDeHierarchy.SYProcess.tryView(model, v).isPresent())
        // .allMatch(
        // v -> memMapping.containsKey(v.getIdentifier()) &&
        // memMapping.get(v.getIdentifier()).size() > 0);
        // var allSDFOk = model.vertexSet().stream().filter(v ->
        // ForSyDeHierarchy.SDFActor.tryView(model, v).isPresent())
        // .allMatch(
        // v -> memMapping.containsKey(v.getIdentifier()) &&
        // memMapping.get(v.getIdentifier()).size() > 0);
        // if (!allSYOk) {
        // return new IdentificationResult(Set.of(), Set.of(
        // "ForSyDeIOSYAndSDFInstrumentedToMemReqIRule: not all SY processes have their
        // memory instrumented"));
        // }
        // if (!allSDFOk) {
        // return new IdentificationResult(Set.of(), Set.of(
        // "ForSyDeIOSYAndSDFInstrumentedToMemReqIRule: not all SDF actors have their
        // memory instrumented"));
        // }
        return new IdentificationResult(Set.of(new InstrumentedMemoryRequirements(processes, channels,
                memMapping.values().stream().flatMap(e -> e.keySet().stream()).collect(Collectors.toSet()),
                memMapping)), Set.of());
    }

}
