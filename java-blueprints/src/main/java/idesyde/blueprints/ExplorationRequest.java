package idesyde.blueprints;

import java.util.HashSet;
import java.util.Set;

import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;

public class ExplorationRequest {

    public DecisionModel decisionModel = null;
    public Set<ExplorationSolution> previousSolutions = new HashSet<>();
    public Explorer.Configuration configuration = new Explorer.Configuration();
}
