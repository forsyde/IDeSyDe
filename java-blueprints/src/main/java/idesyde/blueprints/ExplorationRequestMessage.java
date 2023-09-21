package idesyde.blueprints;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;

import java.util.Set;

@JsonSerialize
public record ExplorationRequestMessage(
                @JsonProperty("model_message") DecisionModelMessage modelMessage,
                @JsonProperty("previous_solutions") Set<ExplorationSolution> previousSolutions,
                Explorer.Configuration configuration) {
}
