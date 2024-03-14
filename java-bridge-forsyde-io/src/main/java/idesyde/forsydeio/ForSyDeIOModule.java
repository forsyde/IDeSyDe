package idesyde.forsydeio;

import forsyde.io.core.ModelHandler;
import idesyde.blueprints.StandaloneModule;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.*;

import java.util.Optional;
import java.util.Set;

public interface ForSyDeIOModule extends StandaloneModule {

    @Override
    default Optional<DesignModel> fromOpaqueDesign(OpaqueDesignModel opaque) {
        ModelHandler modelHandler = ForSyDeIODesignModel.modelHandler;
        if (modelHandler.canLoadModel(opaque.format())) {
            return opaque.asString().flatMap(body -> {
                try {
                    return Optional.of(modelHandler.readModel(body, opaque.format()));
                } catch (Exception e) {
                    e.printStackTrace();
                    return Optional.empty();
                }
            }).map(ForSyDeIODesignModel::new);
        } else {
            return Optional.empty();
        }
    }

    @Override
    default Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel opaque) {
        return switch (opaque.category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore" ->
                opaque.asCBORBinary().flatMap(b -> readFromCBORBytes(b,
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class))
                        .or(() -> opaque.asJsonString()
                                .flatMap(s -> readFromJsonString(s,
                                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class)))
                        .map(m -> (DecisionModel) m);
            case "AperiodicAsynchronousDataflowToPartitionedTiledMulticore" ->
                opaque.asCBORBinary().flatMap(b -> readFromCBORBytes(b,
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class))
                        .or(() -> opaque.asJsonString().flatMap(
                                s -> readFromJsonString(s,
                                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class)))
                        .map(m -> (DecisionModel) m);
            default -> Optional.empty();
        };
    }
}
