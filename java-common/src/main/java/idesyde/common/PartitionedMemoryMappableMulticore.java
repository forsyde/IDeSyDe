package idesyde.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model that captures a paritioned-scheduled memory mappable multicore machine
 *
 * This means that every processing element hosts and has affinity for one and only one
 * runtime element. This runtime element can execute according to any scheduling policy, but
 * it must control only its host.
 */
@JsonSerialize
public record PartitionedMemoryMappableMulticore(
    MemoryMappableMultiCore hardware,
    RuntimesAndProcessors runtimes
) implements DecisionModel {
    @Override
    public DecisionModelHeader header() {
        return new DecisionModelHeader(
                "PartitionedMemoryMappableMulticore",
                Stream.concat(hardware.header().coveredElements().stream(), runtimes.header().coveredElements().stream()).collect(Collectors.toSet()),
                Optional.empty()
        );
    }
}
