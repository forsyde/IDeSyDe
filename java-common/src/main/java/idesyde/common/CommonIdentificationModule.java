package idesyde.common;

import idesyde.blueprints.IdentificationModuleBlueprint;
import idesyde.core.DecisionModel;
import idesyde.core.IdentificationModule;
import idesyde.core.IdentificationRule;
import idesyde.core.ReverseIdentificationRule;
import idesyde.core.headers.DecisionModelHeader;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class CommonIdentificationModule implements IdentificationModuleBlueprint {
    @Override
    public String uniqueIdentifier() {
        return "CommonIdentificationModule";
    }

    @Override
    public Optional<DecisionModel> decisionHeaderToModel(DecisionModelHeader header) {
        return Optional.empty();
    }

    @Override
    public Set<ReverseIdentificationRule<?>> reverseIdentificationRules() {
        return null;
    }

    @Override
    public Set<IdentificationRule<?>> identificationRules() {
        return Set.of();
    }

    public static void main(String[] args) throws IOException {
        final var mod = new CommonIdentificationModule();
        mod.standaloneIdentificationModule(args);
    }

}
