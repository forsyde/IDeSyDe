package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.Optional;

@JsonSerialize
public record DecisionModelMessage(
        DecisionModelHeader header,
        Optional<String> body
) {

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
        return new DecisionModelMessage(model.header(), model.bodyAsText());
    }

    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
    static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
}
