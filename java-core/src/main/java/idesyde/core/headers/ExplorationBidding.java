package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;

@JsonSerialize
public record ExplorationBidding(
        @JsonProperty("can_explore")
        Boolean canExplore,
        Map<String, Double> characteristics
) {
}
