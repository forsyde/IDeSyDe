package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model capturing the binding between procesing element and runtimes.
 *
 * A runtime here is used in a loose sense: it can be simply a programmable bare-metal
 * environment. The assumption is that every runtime has one processing element host and all
 * processing elements might have only one runtime that it is affine to. A processing
 * element having affinity to a runtime simply means that this runtime is managing the
 * processing element according to any policy.
 */
public record RuntimesAndProcessors(
        @JsonProperty("is_bare_metal") List<String> isBareMetal,
        @JsonProperty("is_earliest_deadline_first") List<String> isEarliestDeadlineFirst,
        @JsonProperty("is_fixed_priority") List<String> isFixedPriority,
        @JsonProperty("is_preemptive") List<String> isPreemptive,
        @JsonProperty("is_super_loop") List<String> isSuperLoop,
        @JsonProperty("processor_affinities") Map<String, String> processorAffinities,
        Set<String> processors,
        @JsonProperty("runtime_host") Map<String, String> runtimeHost,
        Set<String> runtimes
) implements DecisionModel {

    @Override
    public DecisionModelHeader header() {
        return new DecisionModelHeader(
                "RuntimesAndProcessors",
                Stream.concat(processors.stream(), runtimes.stream()).collect(Collectors.toSet()), 
                Optional.empty()
        );
    }
}
