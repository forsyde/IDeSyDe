package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonSerialize
public record AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL(
		@JsonProperty("aperiodic_asynchronous_dataflows") Set<AperiodicAsynchronousDataflow> aperiodicAsynchronousDataflows,
		@JsonProperty("partitioned_mem_mappable_multicore_and_pl") PartitionedMemoryMappableMulticoreAndPL partitionedMemMappableMulticoreAndPl,
		@JsonProperty("instrumented_computation_times") InstrumentedComputationTimes instrumentedComputationTimes,
		@JsonProperty("instrumented_memory_requirements") InstrumentedMemoryRequirements instrumentedMemoryRequirements,
		@JsonProperty("hardware_implementation_area") HardwareImplementationArea hardwareImplementationArea,
		@JsonProperty("processes_to_runtime_scheduling") Map<String, String> processesToRuntimeScheduling,
		@JsonProperty("processes_to_logic_programmable_areas") Map<String, String> processesToLogicProgrammableAreas,
		@JsonProperty("processes_to_memory_mapping") Map<String, String> processesToMemoryMapping,
		@JsonProperty("buffer_to_memory_mappings") Map<String, String> bufferToMemoryMappings,
		@JsonProperty("super_loop_schedules") Map<String, List<String>> superLoopSchedules,
		@JsonProperty("processing_elements_to_routers_reservations") Map<String, Map<String, Integer>> processingElementsToRoutersReservations)
		implements DecisionModel {
	@Override
	public Set<String> part() {
		return Stream.concat(aperiodicAsynchronousDataflows.stream().flatMap(x -> x.part().stream()),
						Stream.concat(partitionedMemMappableMulticoreAndPl.part().stream(),
								instrumentedComputationTimes.part().stream()))
				.collect(Collectors.toSet());
	}
}
