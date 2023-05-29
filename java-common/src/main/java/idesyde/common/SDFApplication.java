package idesyde.common;

import java.util.Map;
import java.util.Vector;

public record SDFApplication(
        Vector<String> actorsIdentifiers,
        Vector<String> channelsIdentifiers,
        Vector<String> topologySrcs,
        Vector<String> topologyDsts,
        Vector<Integer> topologyEdgeValue,
        Vector<Long> actorSizes,
        Vector<Map<String, Map<String, Long>>> actorComputationalNeeds,
        Vector<Integer> channelNumInitialTokens,
        Vector<Long> channelTokenSizes,
        Vector<Double> minimumActorThroughputs
        ) {
}
