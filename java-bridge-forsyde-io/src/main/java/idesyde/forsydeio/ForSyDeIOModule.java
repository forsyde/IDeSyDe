package idesyde.forsydeio;

import forsyde.io.core.ModelHandler;
import idesyde.blueprints.StandaloneModule;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.*;

import java.util.Optional;
import java.util.Set;

public class ForSyDeIOModule implements StandaloneModule {

    ModelHandler modelHandler = ForSyDeIODesignModel.modelHandler;

    @Override
    public Optional<DesignModel> fromOpaqueDesign(OpaqueDesignModel opaque) {
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
        // var pathOpt = opaque.format().modelPaths().stream().map(x -> Paths.get(x))
        // .filter(x -> modelHandler.canLoadModel(x)).findAny();
        // var extIdxOpt = pathOpt.map(x -> x.getFileName().toString().indexOf("."));
        // var extOpt = extIdxOpt.flatMap(x -> pathOpt.map(p ->
        // p.getFileName().toString().substring(x + 1)));
        // return opaque.body().flatMap(b -> extOpt.flatMap(ext -> {
        // try {
        // return Optional.of(new ForSyDeIODesignModel(modelHandler.readModel(b, ext)));
        // } catch (Exception e) {
        // e.printStackTrace();
        // return Optional.empty();
        // }
        // }));
    }

    @Override
    public Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel opaque) {
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

    @Override
    public String uniqueIdentifier() {
        return "ForSyDeIOJavaModule";
    }

    @Override
    public Set<ReverseIdentificationRule> reverseIdentificationRules() {
        return Set.of(
                new AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreReverseIdentification(),
                new AperiodicAsynchronousDataflowToPartitionedTiledMulticoreReverseIdentification());
    }

    @Override
    public Set<IdentificationRule> identificationRules() {
        return Set.of(
                new MemoryMappableMultiCoreIRule(),
                new ForSyDeIOSYNetworkToAADataflowIRule(),
                new ForSyDeIOSYAndSDFInstrumentedToMemReqIRule(),
                new TiledMultiCoreIRule(),
                new InstrumentedComputationTimesIRule(),
                new ForSyDeIOSDFToCommon());
    }

    public static void main(String[] args) {
        var server = new ForSyDeIOModule().standaloneModule(args);
        server.ifPresent(s -> s.start(0));
    }
}
