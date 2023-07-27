package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model that combines one type of application, platform and information to bind
 * them.
 *
 * The assumptions of this decision model are: 1. For every process, there is at least one
 * processing element in the platform that can run it. Otherwise, even the trivial mapping
 * is impossible. 2. Super loop schedules are self-timed and stall the processing element
 * that is hosting them. That is, if we have a poor schedule, the processing element will
 * get "blocked" often.
 */
public record AperiodicAsynchronousDataflowToPartitionedTiledMulticore(
        @JsonProperty("aperiodic_asynchronous_dataflow") AperiodicAsynchronousDataflow aperiodicAsynchronousDataflow,
        @JsonProperty("buffer_to_memory_mappings") Map<String, String> bufferToMemoryMappings,
        @JsonProperty("buffer_to_routers_reservations") Map<String, Map<String, List<Long>>> bufferToRoutersReservations,
        @JsonProperty("instrumented_computation_times") InstrumentedComputationTimes instrumentedComputationTimes,
        @JsonProperty("partitioned_tiled_multicore") PartitionedTiledMulticore partitionedTiledMulticore,
        @JsonProperty("processes_to_memory_mapping") Map<String, String> processesToMemoryMapping,
        @JsonProperty("processes_to_runtime_scheduling") Map<String, String> processesToRuntimeScheduling,
        @JsonProperty("super_loop_schedules") Map<String, List<String>> superLoopSchedules
) implements DecisionModel {
    @Override
    public DecisionModelHeader header() {
        return new DecisionModelHeader(
                "AperiodicAsynchronousDataflowToPartitionedTiledMulticore",
                Stream.concat(aperiodicAsynchronousDataflow.header().coveredElements().stream(),
                        Stream.concat(partitionedTiledMulticore.header().coveredElements().stream(),
                                instrumentedComputationTimes.header().coveredElements().stream())).collect(Collectors.toSet()),
                Optional.empty()
        );
    }
}
