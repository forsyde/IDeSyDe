package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonSerialize
public record AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore(
		@JsonProperty("aperiodic_asynchronous_dataflows") Set<AperiodicAsynchronousDataflow> aperiodicAsynchronousDataflows,
		@JsonProperty("partitioned_mem_mappable_multicore") PartitionedMemoryMappableMulticore partitionedMemMappableMulticore,
		@JsonProperty("instrumented_computation_times") InstrumentedComputationTimes instrumentedComputationTimes,
		@JsonProperty("processes_to_runtime_scheduling") Map<String, String> processesToRuntimeScheduling,
		@JsonProperty("processes_to_memory_mapping") Map<String, String> processesToMemoryMapping,
		@JsonProperty("buffer_to_memory_mappings") Map<String, String> bufferToMemoryMappings,
		@JsonProperty("super_loop_schedules") Map<String, List<String>> superLoopSchedules,
		@JsonProperty("processing_elements_to_routers_reservations") Map<String, Map<String, Integer>> processingElementsToRoutersReservations)
		implements DecisionModel {
	@Override
	public DecisionModelHeader header() {
		return new DecisionModelHeader("AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore", Stream
				.concat(aperiodicAsynchronousDataflows.stream().flatMap(x -> x.header().coveredElements().stream()),
						Stream.concat(partitionedMemMappableMulticore.header().coveredElements().stream(),
								instrumentedComputationTimes.header().coveredElements().stream()))
				.collect(Collectors.toSet()), Optional.empty());
	}
}
