package idesyde.blueprints;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.ExplorationSolution;

import java.util.Map;

@JsonSerialize
public record ExplorationSolutionMessage(Map<String, Double> objectives, DecisionModelMessage solved) {

    public static ExplorationSolutionMessage from(ExplorationSolution explorationSolution) {
        return new ExplorationSolutionMessage(explorationSolution.objectives(), DecisionModelMessage.from(explorationSolution.solved()));
    }
}
