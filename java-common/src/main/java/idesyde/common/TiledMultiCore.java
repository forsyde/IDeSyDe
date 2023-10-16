package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record TiledMultiCore(
                @JsonProperty("communication_elements_bit_per_sec_per_channel") Map<String, Double> communicationElementsBitPerSecPerChannel,
                @JsonProperty("communication_elements_max_channels") Map<String, Integer> communicationElementsMaxChannels,
                @JsonProperty("interconnect_topology_dsts") List<String> interconnectTopologyDsts,
                @JsonProperty("interconnect_topology_srcs") List<String> interconnectTopologySrcs,
                Set<String> memories,
                @JsonProperty("network_interfaces") Set<String> networkInterfaces,
                @JsonProperty("pre_computed_paths") Map<String, Map<String, List<String>>> preComputedPaths,
                Set<String> processors,
                @JsonProperty("processors_frequency") Map<String, Long> processorsFrequency,
                @JsonProperty("processors_provisions") Map<String, Map<String, Map<String, Double>>> processorsProvisions,
                Set<String> routers,
                @JsonProperty("tile_memory_sizes") Map<String, Long> tileMemorySizes) implements DecisionModel {
        @Override
        public DecisionModelHeader header() {
                return new DecisionModelHeader(
                                "TiledMultiCore",
                                Stream.concat(memories.stream(),
                                                Stream.concat(processors.stream(),
                                                                Stream.concat(routers.stream(),
                                                                                networkInterfaces.stream())))
                                                .collect(Collectors.toSet()),
                                Optional.empty());
        }

        public Set<String> communicationElements() {
                var ces = new HashSet<String>();
                ces.addAll(networkInterfaces);
                ces.addAll(routers);
                return ces;
        }
}
