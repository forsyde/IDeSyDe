package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import idesyde.core.DecisionModel;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A decision model to hold the required area that a hardware implementation needs.
 */
public record HardwareImplementationArea(
        @JsonProperty("required_areas") Map<String, Long> requiredAreas
) implements DecisionModel {
    @Override
    public Set<String> part() {
        return Set.of();
    }
}
