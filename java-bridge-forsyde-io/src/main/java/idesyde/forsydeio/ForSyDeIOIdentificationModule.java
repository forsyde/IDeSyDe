package idesyde.forsydeio;

import idesyde.blueprints.DecisionModelMessage;
import idesyde.blueprints.DesignModelMessage;
import idesyde.blueprints.StandaloneIdentificationModule;
import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationRule;
import idesyde.core.ReverseIdentificationRule;

import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;


import forsyde.io.core.ModelHandler;

public class ForSyDeIOIdentificationModule implements StandaloneIdentificationModule {

    ModelHandler modelHandler = ForSyDeIODesignModel.modelHandler;

    @Override
    public Optional<DesignModel> designMessageToModel(DesignModelMessage message) {
        var pathOpt = message.header().modelPaths().stream().map(x -> Paths.get(x)).filter(x -> modelHandler.canLoadModel(x)).findAny();
        var extIdxOpt = pathOpt.map(x -> x.getFileName().toString().indexOf("."));
        var extOpt = extIdxOpt.flatMap(x -> pathOpt.map(p -> p.getFileName().toString().substring(x + 1)));
        return message.body().flatMap(b -> extOpt.flatMap(ext -> {
            try {
                return Optional.of(new ForSyDeIODesignModel(modelHandler.readModel(b, ext)));
            } catch (Exception e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }));
    }

    @Override
    public Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message) {
        switch (message.header().category()) {
            case "AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore":
                return message.body().flatMap(b -> readFromString(b, AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore.class));
            default:
                return Optional.empty();
        }
    }

    @Override
    public String uniqueIdentifier() {
        return "ForSyDeIOIdentificationModule";
    }

    @Override
    public Set<ReverseIdentificationRule> reverseIdentificationRules() {
        return Set.of(
            new AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreReverseIdentification()
        );
    }

    @Override
    public Set<IdentificationRule> identificationRules() {
        return Set.of();
    }

    public static void main(String[] args) {
        var server = new ForSyDeIOIdentificationModule().standaloneIdentificationModule(args);
        server.ifPresent(s -> s.start(0));
    }
}
