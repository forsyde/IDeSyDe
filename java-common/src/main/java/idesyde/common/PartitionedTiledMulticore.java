package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import idesyde.core.DecisionModel;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model that captures a paritioned-scheduled multicore machine
 *
 * This means that every processing element hosts and has affinity for one and only one
 * runtime element. This runtime element can execute according to any scheduling policy, but
 * it must control only its host.
 */
public record PartitionedTiledMulticore(
    TiledMultiCore hardware,
    RuntimesAndProcessors runtimes
) implements DecisionModel {
    @Override
    public Set<String> part() {
        return Stream.concat(hardware.part().stream(), runtimes.part().stream()).collect(Collectors.toSet());
    }
}
