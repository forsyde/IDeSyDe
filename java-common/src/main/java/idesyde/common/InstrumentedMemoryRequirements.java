package idesyde.common;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

/** A decision model to hold memory requirements for processes when executing in processing elements.
 *
 * As the decision model stores these memory requirements in associative arrays (maps), the lack
 * of an association between a process and a processing element means that
 * this process _cannot_ be executed in the processing element.
 *
 */
public record InstrumentedMemoryRequirements(
                @JsonProperty("processes") Set<String> processes,
                Set<String> channels,
                @JsonProperty("processing_elements") Set<String> processingElements,
                @JsonProperty("memory_requirements") Map<String, Map<String, Long>> memoryRequirements)
                implements DecisionModel {

        @Override
        public DecisionModelHeader header() {
                return new DecisionModelHeader(
                                "InstrumentedMemoryRequirements",
                                Stream.concat(processes.stream(), processingElements.stream())
                                                .collect(Collectors.toSet()),
                                Optional.empty());
        }

}
