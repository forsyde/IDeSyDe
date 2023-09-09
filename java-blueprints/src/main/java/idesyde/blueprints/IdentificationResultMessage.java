package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Optional;
import java.util.Set;

@JsonSerialize
public record IdentificationResultMessage(
        Set<DecisionModelMessage> identified,
        Set<String> errors) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public static final ObjectMapper objectMapperCBOR = DecisionModelMessage.objectMapperCBOR;
    public static final ObjectMapper objectMapper = DecisionModelMessage.objectMapper;
}
