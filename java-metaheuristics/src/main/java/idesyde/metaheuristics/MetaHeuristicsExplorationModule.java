package idesyde.metaheuristics;

import idesyde.blueprints.DecisionModelMessage;
import idesyde.blueprints.StandaloneExplorationModule;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.Explorer;
import picocli.CommandLine;

import java.util.Optional;
import java.util.Set;

public class MetaHeuristicsExplorationModule implements StandaloneExplorationModule {
    @Override
    public Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message) {
        switch (message.header().category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                return message.body().flatMap(x -> readDecisionModel(x,
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class));
            case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore":
                return message.body().flatMap(b -> readDecisionModel(b,
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class));
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
        var cli = new StandaloneExplorationModule.ExplorationModuleCLI();
        new CommandLine(cli).execute(args);
        module.standaloneExplorationModuleServer(cli).ifPresent(x -> x.start(0));
    }
}
