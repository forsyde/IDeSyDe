package idesyde.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationModule;
import idesyde.core.headers.DesignModelHeader;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface IdentificationModuleBlueprint extends IdentificationModule {

    default void standaloneIdentificationModule(
            String[] args
    ) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        IdentificationModuleCLI cli = new CommandLine(new IdentificationModuleCLI()).getCommand();
        if (cli.designPath != null) {
            Files.createDirectories(cli.designPath);
            Set<DesignModel> designModels = new HashSet<>();
            for (Path p : Files.list(cli.designPath).toList()) {
                if (p.startsWith("header") && p.endsWith(".msgpack")) {
                    designHeaderToModel(objectMapper.readValue(Files.readAllBytes(p), DesignModelHeader.class))
                            .ifPresent(designModels::add);

                }
                var opt = inputsToDesignModel(p);
                if (opt.isPresent()) {
                    var o = opt.get();
                    var h = o.header();
                    h.modelPaths().add(p.toString());
                    var dest = cli.designPath.resolve(Paths.get("header_%s_%s", h.category(), uniqueIdentifier()));
                    Files.writeString(dest.resolve(".json"), h.asString());
                    Files.write(dest.resolve(".msgpack"), h.asBytes());
                    designModels.add(o);
                }
            }

            if (cli.solvedPath != null && cli.reversePath != null) {
                Files.createDirectories(cli.solvedPath);
                Files.createDirectories(cli.reversePath);
            }

            solvedPathStr.value?.let { solvedPathv ->
                    val solvedPath = Paths.get(solvedPathv)
                reversePathStr.value?.let {reversePathv ->
                        val reversePath = Paths.get(reversePathv)
                    val solvedDecisionModels = Files.list(solvedPath)
                            .filter { f -> f.startsWith("header") }
                        .filter { f -> f.extension == ".msgpack" }
                        .map { f -> MsgPack.decodeFromByteArray(DecisionModelHeader.serializer(), Files.readAllBytes(f)) }
                        .map { f -> decisionHeaderToModel(f) }
                        .toList()
                            .filterNotNull()
                            .toSet()
                    val reIdentified = reverseIdentification(designModels, solvedDecisionModels)
                    for ((i, m) in reIdentified.withIndex()) {
                        val dest = reversePath.resolve(i.toString()).resolve(uniqueIdentifier()).resolve(".msgpack")
                        val header = m.header()
                        val h = outputPath.value?.let { op ->
                                val saved = designModelToOutput(m, Paths.get(op))
                            header.copy(modelPaths = listOf(saved.toString()))
                        } ?: header
                        Files.write(dest, MsgPack.encodeToByteArray(DesignModelHeader.serializer(), h))
                    }
                }
            }
        }

            identifiedPathStr.value?.let {identifiedPathv ->
                    val identifiedPath = Paths.get(identifiedPathv)
                Files.createDirectories(identifiedPath)
                val decisionModels = Files.list(identifiedPath)
                        .filter { f -> f.startsWith("header") }
                    .filter { f -> f.extension == ".msgpack" }
                    .map { f -> MsgPack.decodeFromByteArray(DecisionModelHeader.serializer(), Files.readAllBytes(f)) }
                    .map { f -> decisionHeaderToModel(f) }
                    .toList()
                        .filterNotNull()
                        .toSet()
                val identified = identificationStep(
                        identStep.value?.toLong() ?: 0L,
                        designModels,
                        decisionModels
                )
                for (m in identified) {
                    val header = m.header()
                    val destH = identifiedPath.resolve("header_%016d_%s_%s".format(identStep, header.category, uniqueIdentifier()))
                    val h = if (m is DecisionModelWithBody) {
                        val destB = identifiedPath.resolve("body_%016d_%s_%s".format(identStep, header.category, uniqueIdentifier()))
                        Files.writeString(destB.resolve(".json"), m.getBodyAsText())
                        Files.write(destB.resolve(".msgpack"), m.getBodyAsBytes())
                        header.copy(bodyPath = "body_%016d_%s_%s.msgpack".format(identStep, header.category, uniqueIdentifier()))
                    } else header
                    Files.writeString(destH.resolve(".json"), Json.encodeToString(DecisionModelHeader.serializer(), h))
                    Files.write(destH.resolve(".msgpack"), MsgPack.encodeToByteArray(DecisionModelHeader.serializer(), h))
                }
            }
        }
    }

}
