package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationModule;
import idesyde.core.OpaqueDecisionModel;
import idesyde.core.OpaqueDesignModel;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.UploadedFile;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public interface StandaloneIdentificationModule extends IdentificationModule {

    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory())
            .registerModule(new Jdk8Module());
    static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    Optional<DesignModel> designMessageToModel(DesignModelMessage message);

    Optional<DesignModel> fromOpaqueDesign(OpaqueDesignModel opaque);

    Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel opaque);

    Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message);

    default Optional<Javalin> standaloneIdentificationModule(
            String[] args) {
        var sessionDecisionModels = new ConcurrentHashMap<String, ConcurrentSkipListSet<DecisionModel>>();
        var sessionIdentifiedDecisionModels = new ConcurrentHashMap<String, Deque<DecisionModel>>();
        var sessionDesignModels = new ConcurrentHashMap<String, ConcurrentSkipListSet<DesignModel>>();
        var sessionExploredModels = new ConcurrentHashMap<String, ConcurrentSkipListSet<DecisionModel>>();
        var sessionReversedDesignModels = new ConcurrentHashMap<String, Deque<DesignModel>>();
        try (var server = Javalin.create()) {
            server.put(
                    "/decision",
                    ctx -> {
                        if (ctx.isMultipart() && ctx.queryParamMap().containsKey("session")) {
                            var session = ctx.queryParam("session");
                            if (!sessionDecisionModels.containsKey(session)) {
                                sessionDecisionModels.put(session, new ConcurrentSkipListSet<>());
                            }
                            var decisionModels = sessionDecisionModels.get(session);
                            Optional<OpaqueDecisionModel> opaque = Optional.empty();
                            if (ctx.uploadedFile("cbor") != null) {
                                opaque = OpaqueDecisionModel
                                        .fromCBORBytes(ctx.uploadedFile("cbor").content().readAllBytes());
                            } else if (ctx.uploadedFile("json") != null) {
                                opaque = OpaqueDecisionModel
                                        .fromJsonString(new String(ctx.uploadedFile("json").content().readAllBytes(),
                                                StandardCharsets.UTF_8));
                            }
                            // if (file != null)
                            opaque.flatMap(this::fromOpaqueDecision)
                                    .ifPresentOrElse(m -> {
                                        decisionModels.add(m);
                                        ctx.status(200);
                                        ctx.result("Added");
                                    }, () -> {
                                        ctx.status(500);
                                        ctx.result("Failed to add");
                                    });
                        }
                    })
                    .put(
                            "/explored",
                            ctx -> {
                                if (ctx.isMultipart() && ctx.queryParamMap().containsKey("session")) {
                                    var session = ctx.queryParam("session");
                                    if (!sessionExploredModels.containsKey(session)) {
                                        sessionExploredModels.put(session, new ConcurrentSkipListSet<>());
                                    }
                                    var decisionModels = sessionExploredModels.get(session);
                                    Optional<OpaqueDecisionModel> opaque = Optional.empty();
                                    if (ctx.uploadedFile("cbor") != null) {
                                        opaque = OpaqueDecisionModel
                                                .fromCBORBytes(ctx.uploadedFile("cbor").content().readAllBytes());
                                    } else if (ctx.uploadedFile("json") != null) {
                                        opaque = OpaqueDecisionModel
                                                .fromJsonString(
                                                        new String(ctx.uploadedFile("json").content().readAllBytes(),
                                                                StandardCharsets.UTF_8));
                                    }
                                    // if (file != null)
                                    opaque.flatMap(this::fromOpaqueDecision)
                                            .ifPresentOrElse(m -> {
                                                decisionModels.add(m);
                                                ctx.status(200);
                                                ctx.result("Added");
                                            }, () -> {
                                                ctx.status(500);
                                                ctx.result("Failed to add");
                                            });
                                }
                            })
                    .put(
                            "/design",
                            ctx -> {
                                if (ctx.isMultipart() && ctx.queryParamMap().containsKey("session")) {
                                    var session = ctx.queryParam("session");
                                    if (!sessionDesignModels.containsKey(session)) {
                                        sessionDesignModels.put(session, new ConcurrentSkipListSet<>());
                                    }
                                    var designModels = sessionDesignModels.get(session);
                                    Optional<OpaqueDesignModel> opaque = Optional.empty();
                                    if (ctx.uploadedFile("cbor") != null) {
                                        opaque = OpaqueDesignModel
                                                .fromCBORBytes(ctx.uploadedFile("cbor").content().readAllBytes());
                                    } else if (ctx.uploadedFile("json") != null) {
                                        opaque = OpaqueDesignModel
                                                .fromJsonString(
                                                        new String(ctx.uploadedFile("json").content().readAllBytes(),
                                                                StandardCharsets.UTF_8));
                                    }
                                    // if (file != null)
                                    opaque.flatMap(this::fromOpaqueDesign)
                                            .ifPresentOrElse(m -> {
                                                designModels.add(m);
                                                ctx.status(200);
                                                ctx.result("Added");
                                            }, () -> {
                                                ctx.status(500);
                                                ctx.result("Failed to add");
                                            });
                                }
                            })
                    .post(
                            "/identify",
                            ctx -> {
                                if (ctx.queryParamMap().containsKey("session")) {
                                    String session = ctx.queryParam("session");
                                    Set<DecisionModel> decisionModels = sessionDecisionModels.getOrDefault(session,
                                            Set.of());
                                    Set<DesignModel> designModels = sessionDesignModels.getOrDefault(session,
                                            Set.of());
                                    var result = identification(
                                            designModels,
                                            decisionModels);
                                    // var newIdentified = new
                                    // HashSet<>(result.identified());
                                    // newIdentified.removeAll(decisionModels);
                                    if (!sessionIdentifiedDecisionModels.containsKey(session)) {
                                        sessionIdentifiedDecisionModels.put(session, new ArrayDeque<>());
                                    }
                                    var identifiedDecisionModels = sessionIdentifiedDecisionModels.get(session);
                                    identifiedDecisionModels.addAll(result.identified());
                                    ctx.status(200);
                                    ctx.json(result.errors());
                                    // new IdentificationResultMessage(
                                    // result.identified().stream()
                                    // .map(x -> DecisionModelMessage
                                    // .from(x))
                                    // .collect(Collectors
                                    // .toSet()),
                                    // result.errors()).toJsonString()
                                    // .ifPresent(ctx::result);
                                }
                            })
                    .get("/identified", ctx -> {
                        if (ctx.queryParamMap().containsKey("session")) {
                            String session = ctx.queryParam("session");
                            var identifiedDecisionModels = sessionIdentifiedDecisionModels.getOrDefault(session,
                                    new ArrayDeque<>());
                            if (identifiedDecisionModels.size() > 0) {
                                if (ctx.queryParam("encoding") != null
                                        && ctx.queryParam("encoding").equalsIgnoreCase("cbor")) {
                                    OpaqueDecisionModel.from(identifiedDecisionModels.pop()).toCBORBytes()
                                            .ifPresent(ctx::result);
                                } else {
                                    OpaqueDecisionModel.from(identifiedDecisionModels.pop()).toJsonString()
                                            .ifPresent(ctx::result);
                                }
                            }
                            ctx.status(200);
                            // new IdentificationResultMessage(
                            // result.identified().stream()
                            // .map(x -> DecisionModelMessage
                            // .from(x))
                            // .collect(Collectors
                            // .toSet()),
                            // result.errors()).toJsonString()
                            // .ifPresent(ctx::result);
                        } else {
                            ctx.status(204);
                        }
                    })
                    // .post(
                    // "/identify",
                    // ctx -> {
                    // if (ctx.isMultipartFormData()) {
                    // var decisionModels = new HashSet<DecisionModel>();
                    // var designModels = new HashSet<DesignModel>();
                    // var iteration = ctx.queryParamMap()
                    // .containsKey("iteration")
                    // ? Integer.parseInt(
                    // ctx
                    // .queryParam("iteration"))
                    // : 0;
                    // ctx.formParamMap().forEach((name, entries) -> {
                    // if (name.startsWith("decisionModel")) {
                    // for (var msg : entries) {
                    // DecisionModelMessage
                    // .fromJsonString(msg)
                    // .flatMap(this::decisionMessageToModel)
                    // .ifPresent(decisionModels::add);
                    // }
                    // } else if (name.startsWith(
                    // "designModel")) {
                    // for (var msg : entries) {
                    // DesignModelMessage
                    // .fromJsonString(msg)
                    // .flatMap(this::designMessageToModel)
                    // .ifPresent(designModels::add);

                    // }
                    // }
                    // });
                    // var result = identificationStep(
                    // iteration,
                    // designModels,
                    // decisionModels);
                    // new IdentificationResultMessage(
                    // result.identified().stream()
                    // .map(x -> DecisionModelMessage
                    // .from(x))
                    // .collect(Collectors
                    // .toSet()),
                    // result.errors()).toJsonString()
                    // .ifPresent(ctx::result);
                    // } else {
                    // ctx.status(500);
                    // }
                    // })
                    .post(
                            "/reverse",
                            ctx -> {
                                if (ctx.queryParamMap().containsKey("session")) {
                                    String session = ctx.queryParam("session");
                                    if (!sessionDesignModels.containsKey(session)) {
                                        sessionDesignModels.put(session, new ConcurrentSkipListSet<>());
                                    }
                                    Set<DecisionModel> exploredDecisionModels = sessionExploredModels.getOrDefault(
                                            session,
                                            Set.of());
                                    Set<DesignModel> designModels = sessionDesignModels.get(session);
                                    var reversed = reverseIdentification(
                                            exploredDecisionModels, designModels);
                                    // var newIdentified = new
                                    // HashSet<>(result.identified());
                                    // newIdentified.removeAll(decisionModels);
                                    if (!sessionReversedDesignModels.containsKey(session)) {
                                        sessionReversedDesignModels.put(session, new ArrayDeque<>());
                                    }
                                    var reversedDesignModels = sessionReversedDesignModels.get(session);
                                    identifiedDecisionModels.addAll(reversed);
                                    designModels.addAll(reversed);
                                    ctx.status(200);
                                    ctx.text("OK");
                                    // new IdentificationResultMessage(
                                    // result.identified().stream()
                                    // .map(x -> DecisionModelMessage
                                    // .from(x))
                                    // .collect(Collectors
                                    // .toSet()),
                                    // result.errors()).toJsonString()
                                    // .ifPresent(ctx::result);
                                }
                                // if (ctx.isMultipartFormData()) {
                                // var decisionModels = new HashSet<DecisionModel>();
                                // var designModels = new HashSet<DesignModel>();
                                // ctx.formParamMap().forEach((name, entries) -> {
                                // if (name.startsWith("decisionModel")) {
                                // for (var msg : entries) {
                                // DecisionModelMessage
                                // .fromJsonString(msg)
                                // .flatMap(this::decisionMessageToModel)
                                // .ifPresent(decisionModels::add);
                                // }
                                // } else if (name.startsWith(
                                // "designModel")) {
                                // for (var msg : entries) {
                                // DesignModelMessage
                                // .fromJsonString(msg)
                                // .flatMap(this::designMessageToModel)
                                // .ifPresent(designModels::add);

                                // }
                                // }
                                // });

                                // ctx.result(objectMapper
                                // .writeValueAsString(integrated
                                // .stream()
                                // .map(x -> DesignModelMessage
                                // .from(x))
                                // .collect(Collectors
                                // .toList())));
                                // } else {
                                // ctx.status(500);
                                // }
                            })
                    .get("/reversed", ctx -> {
                        if (ctx.queryParamMap().containsKey("session")) {
                            String session = ctx.queryParam("session");
                            var exploredDesignModels = sessionExploredDesignModels.getOrDefault(session,
                                    new ArrayDeque<>());
                            if (exploredDesignModels.size() > 0) {
                                if (ctx.queryParam("encoding") != null
                                        && ctx.queryParam("encoding").equalsIgnoreCase("cbor")) {
                                    OpaqueDesignModel.from(exploredDesignModels.pop()).toCBORBytes()
                                            .ifPresent(ctx::result);
                                } else {
                                    OpaqueDesignModel.from(exploredDesignModels.pop()).toJsonString()
                                            .ifPresent(ctx::result);
                                }
                            }
                            ctx.status(200);
                            // new IdentificationResultMessage(
                            // result.identified().stream()
                            // .map(x -> DecisionModelMessage
                            // .from(x))
                            // .collect(Collectors
                            // .toSet()),
                            // result.errors()).toJsonString()
                            // .ifPresent(ctx::result);
                        } else {
                            ctx.status(204);
                        }
                    })
                    .exception(
                            Exception.class,
                            (e, ctx) -> {
                                e.printStackTrace();
                            })
                    .updateConfig(config -> {
                        config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.GB);
                        config.jetty.contextHandlerConfig(ctx -> {
                            ctx.setMaxFormContentSize(100000000);
                        });
                        config.http.maxRequestSize = 100000000;
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
