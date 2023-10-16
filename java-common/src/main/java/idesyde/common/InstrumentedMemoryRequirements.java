package idesyde.common;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

public record InstrumentedMemoryRequirements(
                @JsonProperty("processes") Set<String> processes,
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
