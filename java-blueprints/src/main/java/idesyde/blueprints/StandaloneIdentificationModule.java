package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationModule;
import io.javalin.Javalin;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

public interface StandaloneIdentificationModule extends IdentificationModule {

        static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory())
                        .registerModule(new Jdk8Module());
        static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

        Optional<DesignModel> designMessageToModel(DesignModelMessage message);

        Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message);

        default Optional<Javalin> standaloneIdentificationModule(
                        String[] args) {
                var decisionModels = new HashSet<DecisionModel>();
                var solvedDecisionModels = new HashSet<DecisionModel>();
                var designModels = new HashSet<DesignModel>();
                try (var server = Javalin.create()) {
                        server.post(
                                        "/decision",
                                        ctx -> DecisionModelMessage.fromJsonString(ctx.body())
                                                        .flatMap(this::decisionMessageToModel)
                                                        .ifPresent(decisionModels::add))
                                        .post(
                                                        "/solved",
                                                        ctx -> {
                                                                DecisionModelMessage.fromJsonString(ctx.body())
                                                                                .flatMap(this::decisionMessageToModel)
                                                                                .ifPresent(solvedDecisionModels::add);
                                                        })
                                        .post(
                                                        "/design",
                                                        ctx -> {
                                                                if (ctx.isMultipart()) {
                                                                        ctx
                                                                                        .uploadedFiles("messages")
                                                                                        .forEach(message -> {
                                                                                                try (var content = message
                                                                                                                .content()) {
                                                                                                        var msg = new String(
                                                                                                                        content.readAllBytes(),
                                                                                                                        StandardCharsets.UTF_8);
                                                                                                        DesignModelMessage
                                                                                                                        .fromJsonString(msg)
                                                                                                                        .flatMap(this::designMessageToModel)
                                                                                                                        .ifPresent(designModels::add);
                                                                                                } catch (IOException e) {
                                                                                                        throw new RuntimeException(
                                                                                                                        e);
                                                                                                }
                                                                                        });
                                                                } else {
                                                                        DesignModelMessage.fromJsonString(ctx.body())
                                                                                        .flatMap(this::designMessageToModel)
                                                                                        .ifPresent(designModels::add);
                                                                }
                                                        })
                                        .get(
                                                        "/identify",
                                                        ctx -> {
                                                                var iteration = ctx.queryParamMap()
                                                                                .containsKey("iteration")
                                                                                                ? Integer.parseInt(ctx
                                                                                                                .queryParam("iteration"))
                                                                                                : 0;
                                                                var result = identificationStep(
                                                                                iteration,
                                                                                designModels,
                                                                                decisionModels);
                                                                // var newIdentified = new
                                                                // HashSet<>(result.identified());
                                                                // newIdentified.removeAll(decisionModels);
                                                                decisionModels.addAll(result.identified());
                                                                new IdentificationResultMessage(
                                                                                result.identified().stream()
                                                                                                .map(x -> DecisionModelMessage
                                                                                                                .from(x))
                                                                                                .collect(Collectors
                                                                                                                .toSet()),
                                                                                result.errors()).toJsonString()
                                                                                .ifPresent(ctx::result);
                                                        })
                                        .get(
                                                        "/integrate",
                                                        ctx -> {
                                                                var integrated = reverseIdentification(
                                                                                solvedDecisionModels,
                                                                                designModels);
                                                                ctx.result(objectMapper.writeValueAsString(integrated
                                                                                .stream()
                                                                                .map(x -> DesignModelMessage.from(x))
                                                                                .collect(Collectors.toList())));
                                                        })
                                        .exception(
                                                        Exception.class,
                                                        (e, ctx) -> {
                                                                e.printStackTrace();
                                                        });
                        server.events(es -> {
                                es.serverStarted(() -> {
                                        System.out.println("INITIALIZED " + server.port());
                                });
                        });
                        return Optional.of(server);
                } catch (Exception e) {
                        e.printStackTrace();
                        return Optional.empty();
                }
        }

        default <T extends DecisionModel> Optional<T> readFromString(String s, Class<T> cls) {
                try {
                        return Optional.of(objectMapper.readValue(s, cls));
                } catch (JsonProcessingException e) {
                        e.printStackTrace();
                        return Optional.empty();
                }
        }

        @CommandLine.Command(mixinStandardHelpOptions = true)
        class IdentificationModuleCLI {
                @CommandLine.Option(names = { "m",
                                "design-path" }, description = "The path where the design models (and headers) are stored.")
                Path designPath;
                @CommandLine.Option(names = { "i",
                                "identified-path" }, description = "The path where identified decision models (and headers) are stored.")
                Path identifiedPath;
                @CommandLine.Option(names = { "s",
                                "solved-path" }, description = "The path where explored decision models (and headers) are stored.")
                Path solvedPath;
                @CommandLine.Option(names = { "r",
                                "reverse-path" }, description = "The path where reverse identified design models (and headers) are stored.")
                Path reversePath;
                @CommandLine.Option(names = { "o",
                                "output-path" }, description = "The path where final integrated design models are stored, in their original format.")
                Path outputPath;
                @CommandLine.Option(names = { "t",
                                "identification-step" }, description = "The overall identification iteration number.")
                int identStep;
                @CommandLine.Option(names = { "server" }, description = "The type of server to start.")
                String serverType;
        }

}
