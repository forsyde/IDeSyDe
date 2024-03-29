package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.OpaqueDecisionModel;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@JsonSerialize
public record ExplorationSolutionMessage(Map<String, Double> objectives, OpaqueDecisionModel solved) {

    public Optional<String> toJsonString() {
        try {
            // the recreation of the ExplorationSolution object is workaround for a bug in
            // with the deserialization of CBOR on othe modules. If it is sent in JSON, we
            // assume the consumer will only care about the JSON representation.
            return Optional.of(DecisionModel.objectMapper
                    .writeValueAsString(new ExplorationSolution(objectives, new OpaqueDecisionModel(solved.category(),
                            solved.part(), solved.bodyJson(), Optional.empty(), Optional.empty()))));
        } catch (JsonProcessingException ignored) {
            System.out.println("Failed to serialize exploration solution message to JSON string.");
            ignored.printStackTrace();
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

    public static Optional<ExplorationSolutionMessage> fromJsonString(String s) {
        try {
            return Optional.of(DecisionModel.objectMapper.readValue(s, ExplorationSolutionMessage.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<ExplorationSolutionMessage> fromCBORBytes(byte[] b) {
        try {
            return Optional.of(DecisionModel.objectMapperCBOR.readValue(b, ExplorationSolutionMessage.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
