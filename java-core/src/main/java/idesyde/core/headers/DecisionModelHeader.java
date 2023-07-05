package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.io.IOException;
import java.util.Set;

@JsonSerialize
public record DecisionModelHeader(
    String category,
    @JsonProperty("covered_elements")
    Set<String> coveredElements,
    @JsonProperty("body_path")
    String bodyPath
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

    static final ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
}
