package idesyde.forsydeio;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import forsyde.io.core.SystemGraph;
import forsyde.io.core.Vertex;
import idesyde.common.HardwareImplementationArea;
import idesyde.core.*;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy.*;

@AutoRegister(ForSyDeIOModule.class)
class HardwareImplementationAreasIRule implements IdentificationRule {
    @Override
    public IdentificationResult apply(
            Set<? extends DesignModel> designModels,
            Set<? extends DecisionModel> decisionModels) {
        var identified = new HashSet<HardwareImplementationArea>();
        var processes = new HashSet<String>();
        var programmableAreas = new HashSet<String>();
        var requiredAreas = new HashMap<String, Map<String, Long>>();
        var latNumerators = new HashMap<String, Map<String, Long>>();
        var latDenominators = new HashMap<String, Map<String, Long>>();
        var errors = new HashSet<String>();
        var model = new SystemGraph();
        for (var dm : designModels) {
            ForSyDeIODesignModel.tryFrom(dm)
                    .map(ForSyDeIODesignModel::systemGraph)
                    .ifPresent(model::mergeInPlace);
        }
        for (Vertex maybeHWInstr : model.vertexSet()) {
            InstrumentedHardwareBehaviour
                    .tryView(model, maybeHWInstr)
                    .ifPresent(instrumentedProcessForHardware -> {
                        var processIdent2HW = instrumentedProcessForHardware.getIdentifier();
                        processes.add(processIdent2HW);
                        for (Vertex maybePLA : model.vertexSet()) {
                            if (!requiredAreas.containsKey(processIdent2HW)) {
                                requiredAreas.put(processIdent2HW, new HashMap<>());
                            }
                            if (!latNumerators.containsKey(processIdent2HW)) {
                                latNumerators.put(processIdent2HW, new HashMap<>());
                            }
                            if (!latDenominators.containsKey(processIdent2HW)) {
                                latDenominators.put(processIdent2HW, new HashMap<>());
                            }
                            LogicProgrammableModule.tryView(model, maybePLA).ifPresent(programmableLogicArea -> {
                                var procIdent = programmableLogicArea.getIdentifier();
                                programmableAreas.add(procIdent);
                                long area = instrumentedProcessForHardware.resourceRequirements().getOrDefault("FPGA", Map.of()).getOrDefault("Area", 0L); // we can bring back the nice methods for FPGA area later
                                if (area > 0) {
                                    requiredAreas.get(processIdent2HW).put(procIdent, area);
                                } else {
                                    errors.add(
                                            "HardwareImplementationAreasIRule: Could not " +
                                                    "identify hardware implementation area, or <= 0 for " +
                                                    processIdent2HW);
                                }
                                latNumerators.get(processIdent2HW).put(procIdent, instrumentedProcessForHardware.latencyInSecsNumerators().getOrDefault("FPGA", 0L));
                                latDenominators.get(processIdent2HW).put(procIdent, instrumentedProcessForHardware.latencyInSecsDenominators().getOrDefault("FPGA", 1L));
                            });
                        }
                    });
        }
        if (requiredAreas.values().stream().mapToLong(x -> x.values().stream().filter(y -> y > 0).count()).sum() == 0L) {
            errors.add(
                    "HardwareImplementationAreasIRule: No implementations of actors in hardware identified");
        } else {
            identified.add(new HardwareImplementationArea(processes, programmableAreas, requiredAreas, latNumerators, latDenominators));
        }

        return new IdentificationResult(identified, errors);
    }
}
