package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.util.Optional;
import java.util.Set;

@JsonSerialize
public record IdentificationResultMessage(
        Set<DecisionModelMessage> identified,
        Set<String> errors
) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory());
    static final ObjectMapper objectMapper = new ObjectMapper();
}
