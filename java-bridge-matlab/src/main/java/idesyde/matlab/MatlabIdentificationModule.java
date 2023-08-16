package idesyde.matlab;

import idesyde.blueprints.IdentificationModuleBlueprint;
import idesyde.core.DecisionModel;
import idesyde.core.IdentificationModule;
import idesyde.core.IdentificationRule;
import idesyde.core.ReverseIdentificationRule;
import idesyde.core.headers.DecisionModelHeader;

import java.util.Optional;
import java.util.Set;

public class MatlabIdentificationModule implements IdentificationModuleBlueprint {
    @Override
    public String uniqueIdentifier() {
        return "MatlabIdentificationModule";
    }

    @Override
    public Optional<DecisionModel> decisionHeaderToModel(DecisionModelHeader header) {
        return Optional.empty();
    }

    @Override
    public Set<ReverseIdentificationRule> reverseIdentificationRules() {
        return Set.of();
    }

    @Override
    public Set<IdentificationRule> identificationRules() {
        return Set.of(
                new CommunicatingAndTriggeredReactiveWorkloadIRule()
        );
    }
}
