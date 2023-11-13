package idesyde.blueprints;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.OpaqueDecisionModel;

import java.util.Map;
import java.util.Optional;

@JsonSerialize
public record ExplorationSolutionMessage(Map<String, Double> objectives, OpaqueDecisionModel solved) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(DecisionModel.objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> toCBORBytes() {
        try {
            return Optional.of(DecisionModel.objectMapperCBOR.writeValueAsBytes(this));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public static ExplorationSolutionMessage from(ExplorationSolution explorationSolution) {
        return new ExplorationSolutionMessage(explorationSolution.objectives(),
                OpaqueDecisionModel.from(explorationSolution.solved()));
    }
}
