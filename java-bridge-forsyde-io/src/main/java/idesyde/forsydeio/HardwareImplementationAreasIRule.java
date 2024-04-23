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
        for (Vertex maybePA : model.vertexSet()) {
            InstrumentedHardwareBehaviour
                    .tryView(model, maybePA)
                    .ifPresent(hw -> {
                        programmableAreas.add(hw.getIdentifier());
                        for (Vertex maybeProc : model.vertexSet()) {
                            if (!requiredAreas.containsKey(hw.getIdentifier())) {
                                requiredAreas.put(hw.getIdentifier(), new HashMap<>());
                            }
                            LogicProgrammableModule.tryView(model, maybeProc).ifPresent(proc -> {
                                processes.add(proc.getIdentifier());
                                long area = hw.requiredHardwareImplementationArea();
                                if (area > 0) {
                                    requiredAreas.get(hw.getIdentifier()).put(proc.getIdentifier(), area);
                                } else {
                                    errors.add(
                                            "HardwareImplementationAreasIRule: Could not " +
                                                    "identify hardware implementation area, or <= 0 for " +
                                                    hw.getIdentifier());
                                }
                            });
                        }
                    });
        }
        if (requiredAreas.isEmpty()) {
            errors.add(
                    "Error: No implementations of actors in hardware identified");
        }

        return new IdentificationResult(
                Set.of(new HardwareImplementationArea(processes, programmableAreas, requiredAreas)), errors);
    }
}
