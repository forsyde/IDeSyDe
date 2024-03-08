package idesyde.metaheuristics;

import idesyde.core.AutoRegister;
import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import idesyde.core.ExplorationBidding;

import java.util.Set;
import java.util.stream.Stream;

public class JMetalExplorer implements Explorer {

    @Override
    public ExplorationBidding bid(Set<Explorer> explorers, DecisionModel decisionModel) {
        // if (decisionModel instanceof
        // AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
        // return new ExplorationBidding(uniqueIdentifer(), true, Map.of());
        // }
        return Explorer.super.bid(explorers, decisionModel);
    }

    @Override
    public Stream<? extends ExplorationSolution> explore(DecisionModel decisionModel,
            Set<ExplorationSolution> previousSolutions, Configuration configuration) {
        // if (decisionModel instanceof
        // AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
        // aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
        // return
        // exploreAADPMMM(aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore,
        // previousSolutions, configuration);
        // }
        return Explorer.super.explore(decisionModel, previousSolutions, configuration);
    }
}
