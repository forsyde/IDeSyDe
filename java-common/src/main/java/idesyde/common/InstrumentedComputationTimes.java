package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A decision model to hold computation times between processsables and processing
 * elements.
 *
 * As the decision model stores these computation in associative arrays (maps), the lack of
 * an association between a processable and a processing element means that this processable
 * _cannot_ be executed in the processing element.
 *
 * In order to maintain the precision as pristine as possible, the values are stored in a
 * "scaled" manner. That is, there is a scaling factor that denotes the denominator in which
 * all the stored values must be divided by. This enables us to move the computational
 * numbers around as integers. Therefore, for any value in this decision model:
 *
 * actual_value = integer_value / scale_factor
 */
public record InstrumentedComputationTimes(
        @JsonProperty("average_execution_times") Map<String, Map<String, Long>> averageExecutionTimes,
        @JsonProperty("best_execution_times") Map<String, Map<String, Long>> bestExecutionTimes,
        Set<String> processes,
        @JsonProperty("processing_elements") Set<String> processingElements,
        @JsonProperty("scale_factor") Long scaleFactor,
        @JsonProperty("worst_execution_times") Map<String, Map<String, Long>> worstExecutionTimes
) implements DecisionModel {

    @Override
    public DecisionModelHeader header() {
        return new DecisionModelHeader(
                "InstrumentedComputationTimes",
                Stream.concat(processes.stream(), processingElements.stream()).collect(Collectors.toSet()),
                Optional.empty()
        );
    }
}
