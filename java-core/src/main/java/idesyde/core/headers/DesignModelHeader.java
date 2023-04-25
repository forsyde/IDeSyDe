package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Set;

@JsonSerialize
public record DesignModelHeader(
        String category,
        Set<String> elements,
        Set<LabelledArcWithPorts> relations,
        @JsonProperty("model_paths")
        Set<String> modelPaths

) {
}
