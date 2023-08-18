package idesyde.blueprints;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.Set;

@JsonSerialize
public record ExplorationRequestMessage(
        @JsonProperty("explorer_id")
        String explorerIdentifier,
        @JsonProperty("model_message")
        DecisionModelMessage modelMessage,
        Set<Map<String, Double>> objectives
) {
}
