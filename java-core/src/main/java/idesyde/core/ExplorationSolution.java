package idesyde.core;

import idesyde.core.DecisionModel;

import java.util.Map;

public record ExplorationSolution(
        Map<String, Double> objectives,
        DecisionModel solved) {

    public boolean dominates(ExplorationSolution other) {
        return objectives.entrySet()
                .stream()
                .allMatch(e -> other.objectives().containsKey(e.getKey())
                        && e.getValue() <= other.objectives.get(e.getKey()))
                && objectives.entrySet()
                        .stream()
                        .anyMatch(e -> other.objectives().containsKey(e.getKey())
                                && e.getValue() < other.objectives.get(e.getKey()));
    }
}
