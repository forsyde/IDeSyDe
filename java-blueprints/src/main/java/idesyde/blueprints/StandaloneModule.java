package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.Module;
import idesyde.core.*;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

public interface StandaloneModule extends Module {

    ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    default Set<IdentificationRule> identificationRules() {
        return Set.of();
    }

    default Set<ReverseIdentificationRule> reverseIdentificationRules() {
        return Set.of();
    }

    default Optional<DesignModel> fromOpaqueDesign(OpaqueDesignModel opaque) {
        return Optional.empty();
    }

    default Optional<DecisionModel> fromOpaqueDecision(OpaqueDecisionModel opaque) {
        return Optional.empty();
    }

    default Set<String> decisionModelSchemas() {
        return Set.of();
    }

    @Override
    default Set<DesignModel> reverseIdentification(Set<DecisionModel> solvedDecisionModels,
            Set<DesignModel> designModels) {
        return reverseIdentificationRules().stream()
                .flatMap(irrule -> irrule.apply(solvedDecisionModels, designModels).stream())
                .collect(Collectors.toSet());
    }

    @Override
    default IdentificationResult identification(Set<DesignModel> designModels, Set<DecisionModel> decisionModels) {
        return identificationRules().stream()
                .map(identificationRule -> identificationRule.apply(designModels, decisionModels))
                .reduce((res1, res2) -> {
                    var merged = new HashSet<DecisionModel>();
                    var errs = new HashSet<String>();
                    merged.addAll(res1.identified());
                    merged.addAll(res2.identified());
                    errs.addAll(res1.errors());
                    errs.addAll(res2.errors());
                    return new IdentificationResult(merged, errs);
                }).orElse(new IdentificationResult(Set.of(), Set.of()));
        // .filter(m -> !decisionModels.containsAll(m.identified()))
        // .collect(Collectors.toSet());
        // if (stepNumber == 0L) {
        // } else {
        // return identificationRules().stream().filter(x -> !x.usesDesignModels())
        // .flatMap(identificationRule -> identificationRule.apply(designModels,
        // decisionModels).stream())
        // .filter(m -> !decisionModels.contains(m))
        // .collect(Collectors.toSet());
        // }
    }

    default Optional<Javalin> standaloneModule(String[] args) {
        var sessionDesignModels = new ConcurrentHashMap<String, ConcurrentSkipListSet<DesignModel>>();
        var sessionDecisionModels = new ConcurrentHashMap<String, ConcurrentSkipListSet<DecisionModel>>();
        var sessionIdentifiedDecisionModels = new ConcurrentHashMap<String, Deque<DecisionModel>>();
        var sessionExplorationStream = new ConcurrentHashMap<String, Stream<? extends ExplorationSolution>>();
        var sessionExploredModels = new ConcurrentHashMap<String, ConcurrentSkipListSet<DecisionModel>>();
        var sessionReversedDesignModels = new ConcurrentHashMap<String, Deque<DesignModel>>();
        var executor = Executors.newWorkStealingPool(Math.max(Runtime.getRuntime().availableProcessors() / 2, 1));
        try (var server = Javalin.create()) {
            server
                    .get("/info/unique_identifier", ctx -> ctx.result(uniqueIdentifier()))
                    // .put("/decision/{session}", ctx -> {
                    // if (ctx.isMultipart() && ctx.queryParamMap().containsKey("session")) {
                    // var session = ctx.queryParam("session");
                    // if (!sessionDecisionModels.containsKey(session)) {
                    // sessionDecisionModels.put(session, new ConcurrentSkipListSet<>());
                    // }
                    // var decisionModels = sessionDecisionModels.get(session);
                    // Optional<OpaqueDecisionModel> opaque = Optional.empty();
                    // if (ctx.formParam("cbor") != null) {
                    // opaque = OpaqueDecisionModel.fromCBORBytes(ctx.formParam("cbor").getBytes());
                    // } else if (ctx.formParam("json") != null) {
                    // opaque = OpaqueDecisionModel.fromJsonString(ctx.formParam("json"));
                    // }
                    // // if (file != null)
                    // opaque.flatMap(this::fromOpaqueDecision).ifPresentOrElse(m -> {
                    // decisionModels.add(m);
                    // ctx.status(200);
                    // ctx.result("Added");
                    // }, () -> {
                    // ctx.status(500);
                    // ctx.result("Failed to add");
                    // });
                    // }
                    // })
                    // .put("/explored/{session}", ctx -> {
                    // if (ctx.isMultipart() && ctx.queryParamMap().containsKey("session")) {
                    // var session = ctx.queryParam("session");
                    // if (!sessionExploredModels.containsKey(session)) {
                    // sessionExploredModels.put(session, new ConcurrentSkipListSet<>());
                    // }
                    // var decisionModels = sessionExploredModels.get(session);
                    // Optional<OpaqueDecisionModel> opaque = Optional.empty();
                    // if (ctx.uploadedFile("cbor") != null) {
                    // opaque = OpaqueDecisionModel
                    // .fromCBORBytes(ctx.uploadedFile("cbor").content().readAllBytes());
                    // } else if (ctx.uploadedFile("json") != null) {
                    // opaque = OpaqueDecisionModel.fromJsonString(
                    // new String(ctx.uploadedFile("json").content().readAllBytes(),
                    // StandardCharsets.UTF_8));
                    // }
                    // // if (file != null)
                    // opaque.flatMap(this::fromOpaqueDecision).ifPresentOrElse(m -> {
                    // decisionModels.add(m);
                    // ctx.status(200);
                    // ctx.result("Added");
                    // }, () -> {
                    // ctx.status(500);
                    // ctx.result("Failed to add");
                    // });
                    // }
                    // }).put("/design/{session}", ctx -> {
                    // if (ctx.isMultipart() && ctx.queryParamMap().containsKey("session")) {
                    // var session = ctx.queryParam("session");
                    // if (!sessionDesignModels.containsKey(session)) {
                    // sessionDesignModels.put(session, new ConcurrentSkipListSet<>());
                    // }
                    // var designModels = sessionDesignModels.get(session);
                    // Optional<OpaqueDesignModel> opaque = Optional.empty();
                    // if (ctx.uploadedFile("cbor") != null) {
                    // opaque = OpaqueDesignModel
                    // .fromCBORBytes(ctx.uploadedFile("cbor").content().readAllBytes());
                    // } else if (ctx.uploadedFile("json") != null) {
                    // opaque = OpaqueDesignModel.fromJsonString(
                    // new String(ctx.uploadedFile("json").content().readAllBytes(),
                    // StandardCharsets.UTF_8));
                    // }
                    // // if (file != null)
                    // opaque.flatMap(this::fromOpaqueDesign).ifPresentOrElse(m -> {
                    // designModels.add(m);
                    // ctx.status(200);
                    // ctx.result("Added");
                    // }, () -> {
                    // ctx.status(500);
                    // ctx.result("Failed to add");
                    // });
                    // }
                    // })
                    .ws("/identify", ws -> {
                        var logger = LoggerFactory.getLogger("main");
                        Set<DecisionModel> decisionModels = new CopyOnWriteArraySet<>();
                        Set<DesignModel> designModels = new HashSet<>();
                        ws.onBinaryMessage(ctx -> {
                            OpaqueDesignModel.fromCBORBytes(ctx.data()).flatMap(this::fromOpaqueDesign)
                                    .ifPresentOrElse(designModels::add,
                                            () -> OpaqueDecisionModel.fromCBORBytes(ctx.data())
                                                    .flatMap(this::fromOpaqueDecision).ifPresent(decisionModels::add));
                        });
                        ws.onMessage(ctx -> {
                            if (ctx.message().toLowerCase().contains("done")) {
                                logger.info("Running a identification step with %s and %s decision and design models"
                                        .formatted(decisionModels.size(), designModels.size()));
                                var results = identification(designModels, decisionModels);
                                for (var result : results.identified()) {
                                    OpaqueDecisionModel.from(result).toJsonString().ifPresent(bytes -> {
                                        ctx.send(bytes);
                                        decisionModels.add(result);
                                    });
                                }
                                for (var msg : results.errors()) {
                                    ctx.send(msg);
                                }
                                logger.info("Finished a identification step with %s decision models identified"
                                        .formatted(decisionModels.size()));
                                ctx.send("done");
                                // executor.submit(() -> {
                                // });
                            } else {
                                OpaqueDesignModel.fromJsonString(ctx.message()).flatMap(this::fromOpaqueDesign)
                                        .ifPresentOrElse(
                                                designModels::add,
                                                () -> OpaqueDecisionModel.fromJsonString(ctx.message())
                                                        .flatMap(this::fromOpaqueDecision)
                                                        .ifPresent(decisionModels::add));
                            }
                        });
                        ws.onConnect(ctx -> {
                            logger.info("A new identification client connected");
                            ctx.enableAutomaticPings(4, TimeUnit.SECONDS);
                        });
                    }).get("/explorers", ctx -> {
                        ctx.result(objectMapper.writeValueAsString(
                                explorers().stream().map(Explorer::uniqueIdentifier).collect(Collectors.toSet())));
                    }).get("/{explorerName}/bid", ctx -> {
                        explorers().stream()
                                .filter(e -> e.uniqueIdentifier().equalsIgnoreCase(ctx.pathParam("explorerName")))
                                .findAny()
                                .ifPresentOrElse(explorer -> {
                                    if (ctx.isMultipartFormData()) {
                                        ctx.formParamMap().forEach((name, entries) -> {
                                            if (name.startsWith("decisionModel")) {
                                                entries.stream().findAny().ifPresent(msg -> {
                                                    OpaqueDecisionModel.fromJsonString(msg)
                                                            .flatMap(this::fromOpaqueDecision)
                                                            .map(decisionModel -> explorer.bid(explorers(),
                                                                    decisionModel))
                                                            .ifPresent(bid -> {
                                                                try {
                                                                    ctx.result(objectMapper.writeValueAsString(bid));
                                                                } catch (JsonProcessingException e1) {
                                                                    e1.printStackTrace();
                                                                    ctx.status(500);
                                                                }
                                                            });
                                                });
                                            }
                                        });
                                    } else {
                                    }
                                }, () -> {
                                    ctx.status(404);
                                });
                    }).get("/{explorerName}/explored", ctx -> {
                        String session = ctx.queryParam("session");
                        sessionExplorationStream.get(session).forEach(solution -> {
                            if (ctx.queryParam("encoding") != null
                                    && ctx.queryParam("encoding").equalsIgnoreCase("cbor")) {
                                ExplorationSolutionMessage.from(solution).toCBORBytes().ifPresent(ctx::result);
                            } else {
                                ExplorationSolutionMessage.from(solution).toJsonString().ifPresent(ctx::result);
                            }
                        });
                    }).ws("/{explorerName}/explore", ws -> {
                        var logger = LoggerFactory.getLogger("main");
                        AtomicReference<Explorer> explorer = new AtomicReference<>();
                        AtomicReference<Explorer.Configuration> configuration = new AtomicReference<>(
                                new Explorer.Configuration());
                        AtomicReference<DecisionModel> decisionModel = new AtomicReference<>();
                        Set<ExplorationSolution> previousSolutions = new CopyOnWriteArraySet<>();
                        ws.onBinaryMessage(ctx -> {
                            logger.info("Receiving a binary message");
                            var payload = ctx.data();
                            ExplorationSolutionMessage.fromCBORBytes(payload)
                                    .flatMap(esm -> fromOpaqueDecision(esm.solved())
                                            .map(m -> new ExplorationSolution(esm.objectives(), m)))
                                    .ifPresentOrElse(previousSolutions::add,
                                            () -> OpaqueDecisionModel.fromCBORBytes(payload)
                                                    .flatMap(this::fromOpaqueDecision)
                                                    .ifPresentOrElse(x -> {
                                                        logger.info("Setting the decision model for exploration");
                                                        decisionModel.set(x);
                                                    }, () -> Explorer.Configuration
                                                            .fromCBORBytes(payload)
                                                            .ifPresentOrElse(configuration::set, () -> logger.warn(
                                                                    "Failed to parse a binary message during exploration"))));
                        });
                        ws.onMessage(ctx -> {
                            if (ctx.message().toLowerCase().contains("done")) {
                                logger.info("Starting exploration of a %s with %s"
                                        .formatted(decisionModel.get().category(), explorer.get().uniqueIdentifier()));
                                explorer.get()
                                        .explore(decisionModel.get(), previousSolutions, configuration.get())
                                        // .takeWhile(s -> ctx.session.isOpen())
                                        .filter(solution -> !configuration.get().strict
                                                || previousSolutions.stream()
                                                        .noneMatch(other -> other.dominates(solution)))
                                        .forEach(s -> {
                                            previousSolutions.add(s);
                                            ExplorationSolutionMessage.from(s).toJsonString().ifPresent(ctx::send);
                                            logger.info("Sent a solution");
                                        });
                                logger.info("Finished exploration");
                                ctx.send("done");
                                // executor.submit(() -> {
                                // });
                            } else {
                                ExplorationSolutionMessage.fromJsonString(ctx.message())
                                        .flatMap(esm -> fromOpaqueDecision(esm.solved())
                                                .map(m -> new ExplorationSolution(esm.objectives(), m)))
                                        .ifPresentOrElse(previousSolutions::add,
                                                () -> OpaqueDecisionModel.fromJsonString(ctx.message())
                                                        .flatMap(this::fromOpaqueDecision)
                                                        .ifPresentOrElse(decisionModel::set,
                                                                () -> Explorer.Configuration
                                                                        .fromJsonString(ctx.message())
                                                                        .ifPresentOrElse(configuration::set,
                                                                                () -> logger.warn(
                                                                                        "Failed to parse a text message during exploration"))));
                            }
                        });
                        ws.onConnect(ctx -> {
                            logger.info("A client connected to exploration");
                            ctx.enableAutomaticPings(4, TimeUnit.SECONDS);
                            explorers().stream()
                                    .filter(e -> e.uniqueIdentifier().equalsIgnoreCase(ctx.pathParam("explorerName")))
                                    .findAny()
                                    .ifPresentOrElse(explorer::set, ctx::closeSession);
                        });
                    })
                    .ws("/reverse", ws -> {
                        var logger = LoggerFactory.getLogger("main");
                        Set<DecisionModel> exploredDecisionModels = new HashSet<>();
                        Set<DesignModel> designModels = new HashSet<>();
                        ws.onBinaryMessage(ctx -> {
                            OpaqueDesignModel.fromCBORBytes(ctx.data()).flatMap(this::fromOpaqueDesign)
                                    .ifPresentOrElse(designModels::add,
                                            () -> OpaqueDecisionModel.fromCBORBytes(ctx.data())
                                                    .flatMap(this::fromOpaqueDecision)
                                                    .ifPresent(exploredDecisionModels::add));
                        });
                        ws.onMessage(ctx -> {
                            if (ctx.message().toLowerCase().contains("done")) {
                                logger.info("Running a reverse identification with %s and %s decision and design models"
                                        .formatted(exploredDecisionModels.size(), designModels.size()));
                                executor.submit(() -> {
                                    var reversed = reverseIdentification(exploredDecisionModels, designModels);
                                    for (var result : reversed) {
                                        OpaqueDesignModel.from(result).toJsonString().ifPresent(bytes -> {
                                            ctx.send(bytes);
                                            // designModels.add(result);
                                        });
                                    }
                                    logger.info(
                                            "Finished a reverse identification step with %s decision models identified"
                                                    .formatted(designModels.size()));
                                    ctx.send("done");
                                });
                            } else {
                                OpaqueDesignModel.fromJsonString(ctx.message()).flatMap(this::fromOpaqueDesign)
                                        .ifPresentOrElse(
                                                designModels::add,
                                                () -> OpaqueDecisionModel.fromJsonString(ctx.message())
                                                        .flatMap(this::fromOpaqueDecision)
                                                        .ifPresent(exploredDecisionModels::add));
                            }
                        });
                        ws.onConnect(ctx -> {
                            logger.info("A new reverse identification client connected");
                            ctx.enableAutomaticPings(4, TimeUnit.SECONDS);
                        });
                    })
                    .post("/reverse", ctx -> {
                        if (ctx.queryParamMap().containsKey("session")) {
                            String session = ctx.queryParam("session");
                            if (!sessionDesignModels.containsKey(session)) {
                                sessionDesignModels.put(session, new ConcurrentSkipListSet<>());
                            }
                            Set<DecisionModel> exploredDecisionModels = sessionExploredModels.getOrDefault(session,
                                    new ConcurrentSkipListSet<>());
                            Set<DesignModel> designModels = sessionDesignModels.get(session);
                            var reversed = reverseIdentification(exploredDecisionModels, designModels);
                            // var newIdentified = new
                            // HashSet<>(result.identified());
                            // newIdentified.removeAll(decisionModels);
                            if (!sessionReversedDesignModels.containsKey(session)) {
                                sessionReversedDesignModels.put(session, new ArrayDeque<>());
                            }
                            var reversedDesignModels = sessionReversedDesignModels.get(session);
                            reversedDesignModels.addAll(reversed);
                            designModels.addAll(reversed);
                            ctx.status(200);
                            ctx.result("OK");
                        }
                    }).get("/reversed", ctx -> {
                        if (ctx.queryParamMap().containsKey("session")) {
                            String session = ctx.queryParam("session");
                            var reversedDesignModels = sessionReversedDesignModels.getOrDefault(session,
                                    new ArrayDeque<>());
                            if (reversedDesignModels.size() > 0) {
                                if (ctx.queryParam("encoding") != null
                                        && ctx.queryParam("encoding").equalsIgnoreCase("cbor")) {
                                    OpaqueDesignModel.from(reversedDesignModels.pop()).toCBORBytes()
                                            .ifPresent(ctx::result);
                                } else {
                                    OpaqueDesignModel.from(reversedDesignModels.pop()).toJsonString()
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
                    }).exception(Exception.class, (e, ctx) -> {
                        e.printStackTrace();
                    }).updateConfig(config -> {
                        config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.GB);
                        config.jetty.wsFactoryConfig(cfg -> {
                            cfg.setMaxTextMessageSize(100000000);
                            cfg.setMaxBinaryMessageSize(100000000);
                        });
                        config.jetty.contextHandlerConfig(ctx -> {
                            ctx.setMaxFormContentSize(100000000);
                        });
                        config.http.maxRequestSize = 100000000;
                    });
            server.events(es -> {
                es.serverStarted(() -> {
                    System.out.println("INITIALIZED " + server.port() + " " + uniqueIdentifier());
                });
            });
            return Optional.of(server);
        } catch (

        Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    default <T extends DecisionModel> Optional<T> readFromJsonString(String s, Class<T> cls) {
        try {
            return Optional.of(objectMapper.readValue(s, cls));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    default <T extends DecisionModel> Optional<T> readFromCBORBytes(byte[] bytes, Class<T> cls) {
        try {
            return Optional.of(objectMapperCBOR.readValue(bytes, cls));
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    class ModuleCLI {
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
        @CommandLine.Option(names = { "server" }, description = "The type of server to start.")
        String serverType;
    }
}
