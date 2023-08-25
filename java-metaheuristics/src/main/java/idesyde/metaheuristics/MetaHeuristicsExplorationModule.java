package idesyde.metaheuristics;

import idesyde.blueprints.DecisionModelMessage;
import idesyde.blueprints.StandaloneExplorationModule;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.Explorer;
import io.javalin.Javalin;

import java.util.Optional;
import java.util.Set;

public class MetaHeuristicsExplorationModule implements StandaloneExplorationModule  {
    @Override
    public Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message) {
        switch (message.header().category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                return message.body().flatMap(x -> readDecisionModel(x, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class));
            default:
                return Optional.empty();
        }
    }

    @Override
    public Set<Explorer> explorers() {
        return Set.of(new JeneticsExplorer());
    }

    public static void main(String[] args) {
        var module = new MetaHeuristicsExplorationModule();
        module.standaloneExplorationModuleServer(args).ifPresent(x -> x.start(0));
    }
}
