package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.util.Set;

@JsonSerialize
public record DesignModelHeader(
        String category,
        Set<String> elements,
        @JsonProperty("model_paths") Set<String> modelPaths

) {

    public String asString() throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }

    public byte[] asBytes() throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(this);
    }

    static DecisionModelHeader fromString(String s) throws JsonProcessingException {
        return objectMapper.readValue(s, DecisionModelHeader.class);
    }

    static DecisionModelHeader fromBytes(byte[] b) throws IOException {
        return objectMapper.readValue(b, DecisionModelHeader.class);
    }

    public static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory())
            .registerModule(new Jdk8Module());
    public static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
}
