package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@JsonSerialize
public record CommunicatingAndTriggeredReactiveWorkload(
         @JsonProperty("tasks") List<String> tasks,
         @JsonProperty("task_sizes") List<Long> taskSizes,
         @JsonProperty("task_computational_needs") List<Map<String, Map<String, Long>>> taskComputationalNeeds,
         @JsonProperty("data_channels") List<String> dataChannels,
         @JsonProperty("data_channel_sizes") List<Long> dataChannelSizes,
         @JsonProperty("data_graph_src") List<String> dataGraphSrc,
         @JsonProperty("data_graph_dst") List<String> dataGraphDst,
         @JsonProperty("data_graph_message_size") List<Long> dataGraphMessageSize,
         @JsonProperty("periodic_sources") List<String> periodicSources,
         @JsonProperty("periods") List<Double> periods,
         @JsonProperty("offsets") List<Double> offsets,
         @JsonProperty("upsamples") List<String> upsamples,
         @JsonProperty("upsample_repetitive_holds") List<Long> upsampleRepetitiveHolds,
         @JsonProperty("upsample_initial_holds") List<Long> upsampleInitialHolds,
         @JsonProperty("downsamples") List<String> downsamples,
         @JsonProperty("downample_repetitive_skips") List<Long> downampleRepetitiveSkips,
         @JsonProperty("downample_initial_skips") List<Long> downampleInitialSkips,
         @JsonProperty("trigger_graph_src") List<String> triggerGraphSrc,
         @JsonProperty("trigger_graph_dst") List<String> triggerGraphDst,
         @JsonProperty("has_or_trigger_semantics") Set<String> hasORTriggerSemantics
) implements DecisionModel {

    @Override
    public Set<String> part() {
        var elems = new HashSet<String>();
        elems.addAll(tasks);
        elems.addAll(upsamples);
        elems.addAll(downsamples);
        elems.addAll(periodicSources);
        elems.addAll(dataChannels);
        elems.addAll(IntStream.range(0, triggerGraphSrc.size()).mapToObj(i ->
                "trigger=" + triggerGraphSrc.get(i) + ":->" + triggerGraphDst.get(i)
        ).collect(Collectors.toSet()));
        return elems;
    }

}
