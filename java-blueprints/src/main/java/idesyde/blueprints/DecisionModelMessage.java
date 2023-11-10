package idesyde.blueprints;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.Optional;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record DecisionModelMessage(
        DecisionModelHeader header,
        Optional<String> body) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public static Optional<DecisionModelMessage> fromJsonString(String s) {
        try {
            return Optional.of(objectMapper.readValue(s, DecisionModelMessage.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public static DecisionModelMessage from(DecisionModel model) {
        return new DecisionModelMessage(model.header(), model.bodyAsJsonString());
    }

    public static final ObjectMapper objectMapperCBOR = DecisionModelHeader.objectMapperCBOR;
    public static final ObjectMapper objectMapper = DecisionModelHeader.objectMapper;
}
