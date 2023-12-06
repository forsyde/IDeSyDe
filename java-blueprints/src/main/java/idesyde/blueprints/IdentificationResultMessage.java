package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;
import idesyde.core.OpaqueDecisionModel;

import java.util.Optional;
import java.util.Set;

@JsonSerialize
public record IdentificationResultMessage(
        Set<OpaqueDecisionModel> identified,
        Set<String> messages) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public static final ObjectMapper objectMapperCBOR = DecisionModel.objectMapperCBOR;
    public static final ObjectMapper objectMapper = DecisionModel.objectMapper;
}
