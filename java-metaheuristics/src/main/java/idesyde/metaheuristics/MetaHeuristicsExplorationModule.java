package idesyde.metaheuristics;

import idesyde.core.Module;

public interface MetaHeuristicsExplorationModule extends Module {
        // @Override
        // public default Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel
        // message) {
        // switch (message.category()) {
        // case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
        // return message.bodyCBOR()
        // .flatMap(x -> readFromCBORBytes(x,
        // AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class))
        // .or(() -> message.bodyJson()
        // .flatMap(x -> readFromJsonString(x,
        // AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)))
        // .map(x -> (DecisionModel) x);
        // case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore":
        // return message.bodyCBOR().flatMap(
        // x -> readFromCBORBytes(x,
        // AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class))
        // .or(() -> message.bodyJson()
        // .flatMap(x -> readFromJsonString(x,
        // AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)))
        // .map(x -> (DecisionModel) x);
        // default:
        // return Optional.empty();
        // }
        // }

}
