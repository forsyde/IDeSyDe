package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import idesyde.core.DecisionModel;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model to hold the required area that a hardware implementation needs.
 */
public record HardwareImplementationArea(
    Set<String> processes,
    @JsonProperty("programmable_areas") Set<String> programmableAreas,
    @JsonProperty("required_areas") Map<String, Map<String, Long>> requiredAreas,
    @JsonProperty("latencies_numerators") Map<String, Map<String, Long>> latenciesNumerators,
    @JsonProperty("latencies_denominators") Map<String, Map<String, Long>> latenciesDenominators
) implements DecisionModel {
    @Override
    public Set<String> part() {
        return Stream.concat(processes.stream(), programmableAreas.stream()).collect(Collectors.toSet());
    }
}
