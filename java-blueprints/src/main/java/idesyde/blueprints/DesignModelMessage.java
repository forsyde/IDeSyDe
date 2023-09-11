package idesyde.blueprints;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import idesyde.core.DesignModel;
import idesyde.core.headers.DesignModelHeader;

import java.util.Optional;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record DesignModelMessage(
        DesignModelHeader header,
        Optional<String> body) {

    public static DesignModelMessage from(DesignModel m) {
        return new DesignModelMessage(m.header(), m.bodyAsString());
    }

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

    public static final ObjectMapper objectMapperCBOR = DesignModelHeader.objectMapperCBOR;
    public static final ObjectMapper objectMapper = DesignModelHeader.objectMapper;
}
