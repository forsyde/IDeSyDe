package idesyde.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModelWithBody;
import idesyde.core.headers.DecisionModelHeader;
import idesyde.core.headers.LabelledArcWithPorts;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
) implements DecisionModelWithBody {

    @Override
    public DecisionModelHeader header() {
        var elems = new HashSet<String>();
        elems.addAll(tasks);
        elems.addAll(upsamples);
        elems.addAll(downsamples);
        elems.addAll(periodicSources);
        elems.addAll(dataChannels);
        return new DecisionModelHeader(
                "CommunicatingAndTriggeredReactiveWorkload",
                elems,
                IntStream.range(0, triggerGraphSrc.size()).mapToObj(i ->
                    new LabelledArcWithPorts(triggerGraphSrc.get(i), triggerGraphDst.get(i), null, null, "")
                ).collect(Collectors.toSet()),
                null
            );
    }

    @Override
    public String getBodyAsText() throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }

    @Override
    public byte[] getBodyAsBytes() throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(this);
    }

    static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
}
