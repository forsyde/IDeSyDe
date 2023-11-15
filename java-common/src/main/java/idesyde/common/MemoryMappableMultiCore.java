package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model capturing the memory mappable platform abstraction.
 * <p>
 * This type of platform is what one would expect from most COTS platforms and
 * hardware designs, which completely or partially follows a von neumman
 * architecture. This means that the storage elements store both data and
 * instructions and the processors access them going through the communication
 * elements; the latter that form the 'interconnect'.
 */
@JsonSerialize
public record MemoryMappableMultiCore(@JsonProperty("processing_elems") Set<String> processingElems,
        @JsonProperty("storage_elems") Set<String> storageElems,
        @JsonProperty("communication_elems") Set<String> communicationElems,
        @JsonProperty("topology_srcs") List<String> topologySrcs,
        @JsonProperty("topology_dsts") List<String> topologyDsts,
        @JsonProperty("processors_frequency") Map<String, Long> processorsFrequency,
        @JsonProperty("processors_provisions") Map<String, Map<String, Map<String, Double>>> processorsProvisions,
        @JsonProperty("storage_sizes") Map<String, Long> storageSizes,
        @JsonProperty("communication_elements_max_channels") Map<String, Integer> communicationElementsMaxChannels,
        @JsonProperty("communication_elements_bit_per_sec_per_channel") Map<String, Double> communicationElementsBitPerSecPerChannel,
        @JsonProperty("pre_computed_paths") Map<String, Map<String, List<String>>> preComputedPaths)
        implements DecisionModel {
    @Override
    public Set<String> part() {
        return Stream.concat(processingElems.stream(),
                        Stream.concat(storageElems.stream(), communicationElems.stream()))
                        .collect(Collectors.toSet());
    }
}
