package idesyde.metaheuristics;

import idesyde.blueprints.StandaloneModule;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.Explorer;
import idesyde.core.OpaqueDecisionModel;

import java.util.Optional;
import java.util.Set;

public interface MetaHeuristicsExplorationModule extends StandaloneModule {
    @Override
    public default Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel message) {
        switch (message.category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                return message.bodyCBOR()
                        .flatMap(x -> readFromCBORBytes(x,
                                AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class))
                        .or(() -> message.bodyJson()
                                .flatMap(x -> readFromJsonString(x,
                                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)))
                        .map(x -> (DecisionModel) x);
            case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore":
                return message.bodyCBOR().flatMap(
                        x -> readFromCBORBytes(x, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class))
                        .or(() -> message.bodyJson()
                                .flatMap(x -> readFromJsonString(x,
                                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)))
                        .map(x -> (DecisionModel) x);
            default:
                return Optional.empty();
        }
    }

}
