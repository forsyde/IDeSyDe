package idesyde.matlab;

import idesyde.core.DesignModel;
import idesyde.core.headers.DesignModelHeader;
import idesyde.core.headers.LabelledArcWithPorts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record SimulinkReactiveDesignModel(
        Set<String> processes,
        Map<String, Long> processesSizes,
        Set<String> delays,
        Map<String, Long> delaysSizes,
        Set<String> sources,
        Map<String, Long> sourcesSizes,
        Map<String, Double> sourcesPeriods,
        Set<String> constants,
        Set<String> sinks,
        Map<String, Long> sinksSizes,
        Map<String, Double> sinksDeadlines,
        Map<String, Map<String, Map<String, Long>>> processesOperations,
        Map<String, Map<String, Map<String, Long>>> delaysOperations,
        Set<SimulinkArc> links
) implements DesignModel {
    @Override
    public DesignModelHeader header() {
        var elems = new HashSet<String>();
        elems.addAll(processes);
        elems.addAll(delays);
        elems.addAll(sources);
        elems.addAll(constants);
        elems.addAll(sinks);
        return new DesignModelHeader(
                "SimulinkReactiveDesignModel",
                elems,
                links.stream().map(l ->
                        new LabelledArcWithPorts(l.src(), l.dst(), l.srcPort(), l.dstPort(), String.valueOf(l.dataSize()))).collect(Collectors.toSet()
                ),
                new HashSet<>()
        );
    }
}
