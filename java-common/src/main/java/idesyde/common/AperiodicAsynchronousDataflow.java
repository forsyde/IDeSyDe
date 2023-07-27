package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.*;

/**
 * This decision model abstract asynchronous dataflow models that can be described by a
 * repeating job-graph of this asynchronous processes. Two illustratives dataflow models
 * fitting this category are synchronous dataflow models (despite the name) and cyclo-static
 * dataflow models.
 *
 * Assumptions: 1. the job graph is always ready to be executed; or, the model is
 * aperiodic.
 *
 * 2. executing the job graph as presented guarantees that the dataflow processes are live
 * (never deadlocked).
 *
 * 3. The job graph ois weakly connected. If you wish to have multiple "applications", you
 * should generate one decision model for each application.
 */
public record AperiodicAsynchronousDataflow(
    @JsonProperty("buffer_max_size_in_bits") Map<String, Long> bufferMaxSizeInBits,
    Set<String> buffers,
    @JsonProperty("job_graph_dst") List<Long> jobGraphDst,
    @JsonProperty("job_graph_is_strong_precedence") List<Boolean> jobGraphIsStrongPrecedence,
    @JsonProperty("job_graph_src") List<Long> jobGraphSrc,
    @JsonProperty("jobs_of_processes") List<String> jobsOfProcesses,
    @JsonProperty("process_get_from_buffer_in_bits") Map<String, Map<String, Long>> processGetFromBufferInBits,
    @JsonProperty("process_minimum_throughput") Map<String, Double> processMinimumThroughput,
    @JsonProperty("process_path_maximum_latency") Map<String, Map<String, Double>> processPathMaximumLatency,
    @JsonProperty("process_put_in_buffer_in_bits") Map<String, Map<String, Long>> processPutInBufferInBits,
    Set<String> processes
) implements DecisionModel {

    @Override
    public DecisionModelHeader header() {
        var s = new HashSet<String>();
        s.addAll(processes);
        s.addAll(buffers);
        return new DecisionModelHeader(
                "AperiodicAsynchronousDataflow",
                s,
                Optional.empty()
        );
    }

}
