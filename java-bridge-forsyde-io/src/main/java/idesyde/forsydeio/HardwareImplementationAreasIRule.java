package idesyde.forsydeio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import forsyde.io.core.SystemGraph;
import idesyde.common.HardwareImplementationArea;
import idesyde.core.*;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy.*;

@AutoRegister(ForSyDeIOModule.class)
class HardwareImplementationAreasIRule implements IdentificationRule {
    @Override
    public IdentificationResult apply(
        Set<? extends DesignModel> designModels,
        Set<? extends DecisionModel> decisionModels
    ) {
        var requiredAreas = new HashMap<String, Long>();
        var errors = new HashSet<String>();
        var model = new SystemGraph();
        for (var dm : designModels) {
            ForSyDeIODesignModel.tryFrom(dm)
                .map(ForSyDeIODesignModel::systemGraph)
                .ifPresent(model::mergeInPlace);
        }

        model.vertexSet().stream().forEach(v ->
            InstrumentedHardwareBehaviour
                .tryView(model, v)
                .ifPresent(hw -> {
                    long area = hw.requiredHardwareImplementationArea();
                    if (area > 0) {
                        requiredAreas.put(hw.getIdentifier(), area);
                    } else {
                        errors.add(
                            "HardwareImplementationAreasIRule: Could not " +
                            "identify hardware implementation area, or <= 0 for " + 
                            hw.getIdentifier()
                        );
                    } 
                })
        );

        if (requiredAreas.isEmpty()) {
            errors.add(
                "Error: No implementations of actors in hardware identified"
            );
        }

        return new IdentificationResult(
            Set.of(new HardwareImplementationArea(requiredAreas)), errors
        );
    }
}
