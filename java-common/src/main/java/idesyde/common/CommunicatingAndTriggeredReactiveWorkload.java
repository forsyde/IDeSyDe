package idesyde.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@JsonSerialize
public record CommunicatingAndTriggeredReactiveWorkload(
         List<String> tasks,
         List<Long> taskSizes,
         List<Map<String, Map<String, Long>>> taskComputationalNeeds,
         List<String> dataChannels,
         List<Long> dataChannelSizes,
         List<String> dataGraphSrc,
         List<String> dataGraphDst,
         List<Long> dataGraphMessageSize,
         List<String> periodicSources,
         List<Double> periods,
         List<Double> offsets,
         List<String> upsamples,
         List<Long> upsampleRepetitiveHolds,
         List<Long> upsampleInitialHolds,
         List<String> downsamples,
         List<Long> downampleRepetitiveSkips,
         List<Long> downampleInitialSkips,
         List<String> triggerGraphSrc,
         List<String> triggerGraphDst,
         Set<String> hasORTriggerSemantics
) implements DecisionModel {

    @Override
    public DecisionModelHeader header() {
        var elems = new HashSet<String>();
        elems.addAll(tasks);
        elems.addAll(upsamples);
        elems.addAll(downsamples);
        elems.addAll(periodicSources);
        elems.addAll(dataChannels);
        elems.addAll(IntStream.range(0, triggerGraphSrc.size()).mapToObj(i ->
                "trigger=" + triggerGraphSrc.get(i) + ":->" + triggerGraphDst.get(i)
        ).collect(Collectors.toSet()));
        return new DecisionModelHeader(
                "CommunicatingAndTriggeredReactiveWorkload",
                elems,
                null
            );
    }

}
