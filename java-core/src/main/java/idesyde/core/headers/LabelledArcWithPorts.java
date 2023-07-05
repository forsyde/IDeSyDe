package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record LabelledArcWithPorts(
        String src,
        String dst,
        @JsonProperty("src_port")
        String srcPort,
        @JsonProperty("dst_port")
        String dstPort,
        String Label
) {
}
