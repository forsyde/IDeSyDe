package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.headers.DesignModelHeader;

import java.util.Optional;

@JsonSerialize
public record DesignModelMessage(
        DesignModelHeader header,
        Optional<String> body
) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public static Optional<DesignModelMessage> fromJsonString(String s) {
        try {
            return Optional.of(objectMapper.readValue(s, DesignModelMessage.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
    static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
}
