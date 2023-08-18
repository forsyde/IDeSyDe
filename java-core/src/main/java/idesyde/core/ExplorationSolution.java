package idesyde.core;

import idesyde.core.DecisionModel;

import java.util.Map;

public record ExplorationSolution(
    Map<String, Double> objectives,
    DecisionModel solved
) {
}
