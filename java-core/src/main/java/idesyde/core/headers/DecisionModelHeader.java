package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Set;

@JsonSerialize
public record DecisionModelHeader(
    String category,
    @JsonProperty("covered_elements")
    Set<String> coveredElements,
    @JsonProperty("covered_relations")
    Set<LabelledArcWithPorts> coveredRelations,
    @JsonProperty("body_path")
    String bodyPath
) {
}
