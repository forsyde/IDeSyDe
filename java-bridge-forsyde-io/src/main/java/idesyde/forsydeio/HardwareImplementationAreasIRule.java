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
                    .ifPresent(hwInstr -> {
                        var hwIdent = hwInstr.getIdentifier();
                        processes.add(hwIdent);
                        for (Vertex maybeProc : model.vertexSet()) {
                            if (!requiredAreas.containsKey(hwIdent)) {
                                requiredAreas.put(hwIdent, new HashMap<>());
                            }
                            LogicProgrammableModule.tryView(model, maybeProc).ifPresent(proc -> {
                                var procIdent = proc.getIdentifier();
                                programmableAreas.add(procIdent);
                                long area = hwInstr.requiredHardwareImplementationArea();
                                if (area > 0) {
                                    requiredAreas.get(hwIdent).put(procIdent, area);
                                } else {
                                    errors.add(
                                            "HardwareImplementationAreasIRule: Could not " +
                                                    "identify hardware implementation area, or <= 0 for " +
                                                    hwIdent);
                                }
                            });
                        }
                    });
        }
        if (requiredAreas.isEmpty()) {
            errors.add(
                    "Error: No implementations of actors in hardware identified");
        } else {
            identified.add(new HardwareImplementationArea(processes, programmableAreas, requiredAreas));
        }

        return new IdentificationResult(identified, errors);
    }
}
