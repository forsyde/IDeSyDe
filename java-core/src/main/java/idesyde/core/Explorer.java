package idesyde.core;

import idesyde.core.headers.ExplorationBidding;

import java.util.stream.Stream;

public interface Explorer {

    ExplorationBidding bid(DecisionModel decisionModel);

    Stream<? extends DecisionModel> explore(DesignModel designModel);
}
