package idesyde.blueprints;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.ExplorationSolution;

import java.util.Set;

@JsonSerialize
public record ExplorationRequestMessage(
        @JsonProperty("explorer_id")
        String explorerIdentifier,
        @JsonProperty("model_message")
        DecisionModelMessage modelMessage,
        @JsonProperty("previous_solutions")
        Set<ExplorationSolution> previousSolutions
) {
}
