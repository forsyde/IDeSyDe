package idesyde.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import idesyde.core.DecisionModel;
import idesyde.core.DecisionModelWithBody;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationModule;
import idesyde.core.headers.DecisionModelHeader;
import idesyde.core.headers.DesignModelHeader;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public interface IdentificationModuleBlueprint extends IdentificationModule {
    default void standaloneIdentificationModule(
            String[] args
    ) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
        IdentificationModuleCLI cli = new CommandLine(new IdentificationModuleCLI()).getCommand();
        if (cli.designPath != null) {
            Files.createDirectories(cli.designPath);
            Set<DesignModel> designModels = new HashSet<>();
            for (Path p : Files.list(cli.designPath).toList()) {
//                if (p.startsWith("header") && p.endsWith(".cbor")) {
//                    designHeaderToModel(objectMapper.readValue(Files.readAllBytes(p), DesignModelHeader.class))
//                            .ifPresent(designModels::add);
//
//                }
                var opt = inputsToDesignModel(p);
                if (opt.isPresent()) {
                    var o = opt.get();
                    var h = o.header();
                    h.modelPaths().add(p.toString());
                    var dest = cli.designPath.resolve(Paths.get("header_%s_%s", h.category(), uniqueIdentifier()));
                    Files.writeString(dest.resolve(".json"), h.asString());
                    Files.write(dest.resolve(".cbor"), h.asBytes());
                    designModels.add(o);
                }
            }

            if (cli.solvedPath != null && cli.reversePath != null) {
                Files.createDirectories(cli.solvedPath);
                Files.createDirectories(cli.reversePath);
                var solvedDecisionModels = new HashSet<DecisionModel>();
                for (var p : Files.list(cli.solvedPath).toList()) {
                    if (p.startsWith("header") && p.endsWith(".cbor")) {
                        decisionHeaderToModel(objectMapper.readValue(Files.readAllBytes(p), DecisionModelHeader.class))
                                .ifPresent(solvedDecisionModels::add);
                    }
                }
                var reIdentified = reverseIdentification(solvedDecisionModels, designModels);
                var i = 0;
                for (var m : reIdentified) {
                    var dest = cli.reversePath.resolve(String.valueOf(i)).resolve(uniqueIdentifier()).resolve(".cbor");
                    var header = m.header();
                    if (cli.outputPath != null) {
                        var saved = designModelToOutput(m, cli.outputPath);
                        header.modelPaths().add(saved.toString());
                    }
                    Files.write(dest, header.asBytes());
                    i += 1;
                }
                return;
            }

            if (cli.identifiedPath != null) {
                var decisionModels = new HashSet<DecisionModel>();
                for (var p : Files.list(cli.solvedPath).toList()) {
                    if (p.startsWith("header") && p.endsWith(".cbor")) {
                        decisionHeaderToModel(objectMapper.readValue(Files.readAllBytes(p), DecisionModelHeader.class))
                                .ifPresent(decisionModels::add);
                    }
                }
                var identified = identificationStep(cli.identStep, designModels, decisionModels);
                for (var m : identified) {
                    DecisionModelHeader header = m.header();
                    var destH = cli.identifiedPath.resolve("header_%016d_%s_%s".format(String.valueOf(cli.identStep), header.category(), uniqueIdentifier()));
                    if (m instanceof DecisionModelWithBody modelWithBody) {
                        var destB = cli.identifiedPath.resolve("body_%016d_%s_%s".format(String.valueOf(cli.identStep), header.category(), uniqueIdentifier()));
                        Files.writeString(destB.resolve(".json"), modelWithBody.getBodyAsText());
                        Files.write(destB.resolve(".cbor"), modelWithBody.getBodyAsBytes());
                        header = new DecisionModelHeader(header.category(), header.coveredElements(), "body_%016d_%s_%s.cbor".format(String.valueOf(cli.identStep), header.category(), uniqueIdentifier()));
                    }
                    Files.writeString(destH.resolve(".json"), header.asString());
                    Files.write(destH.resolve(".cbor"), header.asBytes());
                }
            }


        }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    class IdentificationModuleCLI {
        @CommandLine.Option(names = {"m", "design-path"}, description = "The path where the design models (and headers) are stored.")
        Path designPath;
        @CommandLine.Option(names = {"i", "identified-path"}, description = "The path where identified decision models (and headers) are stored.")
        Path identifiedPath;
        @CommandLine.Option(names = {"s", "solved-path"}, description = "The path where explored decision models (and headers) are stored.")
        Path solvedPath;
        @CommandLine.Option(names = {"r", "reverse-path"}, description = "The path where reverse identified design models (and headers) are stored.")
        Path reversePath;
        @CommandLine.Option(names = {"o", "output-path"}, description = "The path where final integrated design models are stored, in their original format.")
        Path outputPath;
        @CommandLine.Option(names = {"t", "identification-step"}, description = "The overall identification iteration number.")
        int identStep;
    }

}
