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
                    errs.addAll(res1.messages());
                    errs.addAll(res2.messages());
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
        var cachedDecisionModels = new ConcurrentHashMap<ByteBuffer, DecisionModel>();
        var cachedSolvedDecisionModels = new ConcurrentHashMap<ByteBuffer, DecisionModel>();
        var cachedDesignModels = new ConcurrentHashMap<ByteBuffer, DesignModel>();
        var cachedReversedDesignModels = new ConcurrentHashMap<ByteBuffer, DesignModel>();
        var sessionExplorationStream = new ConcurrentHashMap<String, Stream<? extends ExplorationSolution>>();
        var sessionReversedDesignModels = new ConcurrentHashMap<String, Deque<DesignModel>>();
        try (var server = Javalin.create()) {
            server
                    .get("/info/unique_identifier", ctx -> ctx.result(uniqueIdentifier()))
                    .get("/info/is_caching", ctx -> ctx.result("true"))
                    .get("/decision/cache/exists",
                            ctx -> {
                                if (ctx.isMultipartFormData()) {
                                    if (cachedDecisionModels.values().stream()
                                            .anyMatch(m -> m.category().equals(ctx.formParam("category")))) {
                                        var parts = cachedDecisionModels.values().stream().map(DecisionModel::part)
                                                .collect(Collectors.toSet());
                                        for (var e : ctx.formParams("part")) {
                                            // System.out.println("Checking if " + e + " is in parts for category " +
                                            // ctx.formParam("category"));
                                            if (parts.stream().noneMatch(s -> s.contains(e))) {
                                                ctx.result("false");
                                                return;
                                            }
                                        }
                                        ctx.result("true");
                                    } else {
                                        ctx.result("false");
                                    }
                                } else {
                                    var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                                    if (cachedDecisionModels.containsKey(bb)) {
                                        // System.out.println("YES decision cache exists of "
                                        // + Arrays.toString(ctx.bodyAsBytes()));
                                        ctx.result("true");
                                    } else {
                                        // System.out.println("NO decision cache exists of "
                                        // + Arrays.toString(ctx.bodyAsBytes()));
                                        ctx.result("false");
                                    }
                                }
                            })
                    .get("/design/cache/exists",
                            ctx -> {
                                if (ctx.isMultipartFormData()) {
                                    if (cachedDesignModels.values().stream()
                                            .anyMatch(m -> m.category().equals(ctx.formParam("category")))) {
                                        var elements = cachedDesignModels.values().stream().map(DesignModel::elements)
                                                .collect(Collectors.toSet());
                                        for (var e : ctx.formParams("elements")) {
                                            if (elements.stream().noneMatch(s -> s.contains(e))) {
                                                ctx.result("false");
                                                return;
                                            }
                                        }
                                        ctx.result("true");
                                    } else {
                                        ctx.result("false");
                                    }
                                } else {
                                    var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                                    if (cachedDesignModels.containsKey(bb)) {
                                        ctx.result("true");
                                    } else {
                                        ctx.result("false");
                                    }
                                }
                            })
                    .get("/solved/cache/exists",
                            ctx -> {
                                if (ctx.isMultipartFormData()) {
                                    if (cachedSolvedDecisionModels.values().stream()
                                            .anyMatch(m -> m.category().equals(ctx.formParam("category")))) {
                                        var elements = cachedSolvedDecisionModels.values().stream()
                                                .map(DecisionModel::part).collect(Collectors.toSet());
                                        for (var e : ctx.formParams("part")) {
                                            if (elements.stream().noneMatch(s -> s.contains(e))) {
                                                ctx.result("false");
                                                return;
                                            }
                                        }
                                        ctx.result("true");
                                    } else {
                                        ctx.result("false");
                                    }
                                } else {
                                    var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                                    if (cachedSolvedDecisionModels.containsKey(bb)) {
                                        ctx.result("true");
                                    } else {
                                        ctx.result("false");
                                    }
                                }
                            })
                    .get("/decision/cache/fetch", ctx -> {
                        var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                        // cachedDecisionModels.stream()
                        // .filter(m -> m.globalSHA2Hash().map(hash -> Arrays.equals(hash,
                        // ctx.bodyAsBytes()))
                        // .orElse(false))
                        // .findAny()
                        // .map(OpaqueDecisionModel::from)
                        // .flatMap(OpaqueDecisionModel::toJsonString)
                        // .ifPresentOrElse(ctx::result, () -> ctx.result("Not in cache"));
                        if (cachedDecisionModels.containsKey(bb)) {
                            OpaqueDecisionModel.from(cachedDecisionModels.get(bb)).toJsonString()
                                    .ifPresentOrElse(ctx::result, () -> ctx.result("Not in cache"));
                        } else {
                            ctx.result("Not in cache");
                            ctx.status(404);
                        }
                    })
                    .get("/design/cache/fetch", ctx -> {
                        var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                        if (cachedDesignModels.containsKey(bb)) {
                            OpaqueDesignModel.from(cachedDesignModels.get(bb)).toJsonString()
                                    .ifPresentOrElse(ctx::result, () -> ctx.result("Not in cache"));
                        } else {
                            ctx.status(404);
                        }
                    })
                    .get("/solved/cache/fetch", ctx -> {
                        var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                        if (cachedSolvedDecisionModels.containsKey(bb)) {
                            OpaqueDecisionModel.from(cachedSolvedDecisionModels.get(bb))
                                    .toJsonString().ifPresent(ctx::result);
                        } else {
                            ctx.result("Not in cache");
                            ctx.status(404);
                        }
                    })
                    .get("/reversed/cache/fetch", ctx -> {
                        var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                        if (cachedReversedDesignModels.containsKey(bb)) {
                            OpaqueDesignModel.from(cachedReversedDesignModels.get(bb))
                                    .toJsonString().ifPresent(ctx::result);
                        } else {
                            ctx.status(404);
                        }
                    })
                    .post("/decision/cache/add",
                            ctx -> {
                                // System.out.println("Adding to decision cache: " + ctx.body());
                                if (ctx.isMultipartFormData()) {
                                    OpaqueDecisionModel.fromJsonString(ctx.formParam("decisionModel"))
                                            .ifPresentOrElse(opaque -> {
                                                var bb = ByteBuffer.wrap(opaque.globalSHA2Hash().get()); // TODO: fix
                                                                                                         // possibl NPE
                                                                                                         // later
                                                fromOpaqueDecision(opaque).ifPresentOrElse(m -> {
                                                    // System.out.println("Adding non-opaque to decision cache: "
                                                    // + m.globalSHA2Hash().map(Arrays::toString).orElse("NO HASH"));
                                                    cachedDecisionModels.put(bb, m);
                                                }, () -> {
                                                    // System.out.println("Adding opaque to decision cache: "
                                                    // + opaque.globalSHA2Hash().map(Arrays::toString).orElse("NO
                                                    // HASH"));
                                                    cachedDecisionModels.put(bb, opaque);
                                                });
                                                ctx.status(200);
                                                ctx.result(opaque.globalSHA2Hash().map(Arrays::toString)
                                                        .orElse("NO HASH"));
                                                // opaque.globalSHA2Hash().ifPresent(hash -> cachedDecisionModels
                                                // .put(ByteBuffer.wrap(hash), fromOpaqueDecision(opaque)));
                                            }, () -> ctx.status(500));
                                }
                            })
                    .post("/solved/cache/add",
                            ctx -> {
                                if (ctx.isMultipartFormData()) {
                                    OpaqueDecisionModel.fromJsonString(ctx.formParam("solvedModel"))
                                            .flatMap(this::fromOpaqueDecision)
                                            .ifPresent(m -> m.globalSHA2Hash().ifPresent(
                                                    hash -> cachedSolvedDecisionModels.put(ByteBuffer.wrap(hash), m)));
                                }
                            })
                    .post("/design/cache/add",
                            ctx -> {
                                if (ctx.isMultipartFormData()) {
                                    OpaqueDesignModel.fromJsonString(ctx.formParam("designModel")).ifPresent(opaque -> {
                                        fromOpaqueDesign(opaque).ifPresentOrElse(m -> {
                                            // System.out.println("Adding non opaque design model to cache: " +
                                            // m.category());
                                            cachedDesignModels.put(ByteBuffer.wrap(opaque.globalSHA2Hash().get()), m);
                                        }, () -> cachedDesignModels.put(ByteBuffer.wrap(opaque.globalSHA2Hash().get()),
                                                opaque));
                                    });
                                    ctx.status(200);
                                    ctx.result("OK");
                                }
                            })
                    .post("/reversed/cache/add",
                            ctx -> {
                                if (ctx.isMultipartFormData()) {
                                    OpaqueDesignModel.fromJsonString(ctx.formParam("reversedModel"))
                                            .flatMap(this::fromOpaqueDesign)
                                            .ifPresent(m -> m.globalSHA2Hash().ifPresent(
                                                    hash -> cachedReversedDesignModels.put(ByteBuffer.wrap(hash), m)));
                                }
                            })
                    .post("/decision/cache/clear", ctx -> cachedDecisionModels.clear())
                    .post("/design/cache/clear", ctx -> cachedDesignModels.clear())
                    .post("/solved/cache/clear", ctx -> cachedSolvedDecisionModels.clear())
                    .post("/reversed/cache/clear", ctx -> cachedReversedDesignModels.clear())
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
                    .post("/identify", ctx -> {
                        var logger = LoggerFactory.getLogger("main");
                        Set<DecisionModel> decisionModels = new HashSet<>();
                        Set<DesignModel> designModels = new HashSet<>();
                        cachedDecisionModels.values().forEach(decisionModels::add);
                        cachedDesignModels.values().forEach(designModels::add);
                        logger.debug("Running a identification step with %s and %s decision and design models"
                                .formatted(decisionModels.size(), designModels.size()));
                        var results = identification(designModels, decisionModels);
                        for (var result : results.identified()) {
                            result.globalSHA2Hash().ifPresent(hash -> {
                                // System.out.println("Adding a %s decision model with hash %s to
                                // cache".formatted(result.category(), Arrays.toString(hash)));
                                cachedDecisionModels.put(ByteBuffer.wrap(hash), result);
                            });
                        }
                        logger.debug("Finished a identification step with %s decision models identified".formatted(
                                results.identified().size()));
                        ctx.json(IdentificationResultCompactMessage.from(results));
                    })
                    .ws("/identify", ws -> {
                        var logger = LoggerFactory.getLogger("main");
                        Set<DecisionModel> decisionModels = new CopyOnWriteArraySet<>();
                        Set<DesignModel> designModels = new CopyOnWriteArraySet<>();
                        ws.onBinaryMessage(ctx -> {
                            OpaqueDesignModel.fromCBORBytes(ctx.data()).flatMap(this::fromOpaqueDesign)
                                    .ifPresentOrElse(designModels::add,
                                            () -> OpaqueDecisionModel.fromCBORBytes(ctx.data())
                                                    .flatMap(this::fromOpaqueDecision).ifPresent(decisionModels::add));
                        });
                        ws.onMessage(ctx -> {
                            if (ctx.message().toLowerCase().contains("done")) {
                                logger.debug("Running a identification step with %s and %s decision and design models"
                                        .formatted(decisionModels.size(), designModels.size()));
                                var results = identification(designModels, decisionModels);
                                for (var msg : results.messages()) {
                                    ctx.send(msg);
                                }
                                for (var result : results.identified()) {
                                    decisionModels.add(result);
                                    OpaqueDecisionModel.from(result).toJsonString().ifPresent(bytes -> {
                                        if (ctx.session.isOpen())
                                            ctx.send(bytes);
                                    });
                                }
                                logger.debug("Finished a identification step with %s decision models identified"
                                        .formatted(decisionModels.size()));
                                if (ctx.session.isOpen())
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
                            logger.debug("A new identification client connected");
                            ctx.enableAutomaticPings(1, TimeUnit.SECONDS);
                            decisionModels.clear();
                            designModels.clear();
                        });
                        ws.onError(ctx -> {
                            logger.error("An error occurred in the identification websocket");
                            if (ctx.session.isOpen()) {
                                ctx.send("done");
                                ctx.closeSession();
                            }
                            // ctx.send("ERROR " + ctx.error().getMessage());
                            // ctx.send("done");
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
                                                            .map(decisionModel -> explorer.bid(decisionModel))
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
                                        var bb = ByteBuffer.wrap(ctx.bodyAsBytes());
                                        // System.out.println(
                                        // "Bidding with %s and %s".formatted(Arrays.toString(ctx.bodyAsBytes()),
                                        // explorer.uniqueIdentifier()));
                                        var decisionModel = cachedDecisionModels.get(bb);
                                        var bid = explorer.bid(decisionModel);
                                        try {
                                            // System.out.println("returning bidding value");
                                            ctx.result(objectMapper.writeValueAsString(bid));
                                        } catch (JsonProcessingException e1) {
                                            e1.printStackTrace();
                                            ctx.status(500);
                                        }
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
                            logger.debug("Receiving a binary message");
                            var payload = ctx.data();
                            ExplorationSolutionMessage.fromCBORBytes(payload)
                                    .flatMap(esm -> fromOpaqueDecision(esm.solved())
                                            .map(m -> new ExplorationSolution(esm.objectives(), m)))
                                    .ifPresentOrElse(previousSolutions::add,
                                            () -> OpaqueDecisionModel.fromCBORBytes(payload)
                                                    .flatMap(this::fromOpaqueDecision)
                                                    .ifPresentOrElse(decisionModel::set,
                                                            () -> Explorer.Configuration
                                                                    .fromCBORBytes(payload)
                                                                    .ifPresent(configuration::set)));
                        });
                        ws.onMessage(ctx -> {
                            logger.debug("Receiving a text message during exploration");
                            if (ctx.message().toLowerCase().contains("done")) {
                                logger.debug("Starting exploration of a %s with %s"
                                        .formatted(decisionModel.get().category(), explorer.get().uniqueIdentifier()));
                                explorer.get()
                                        .explore(decisionModel.get(),
                                                previousSolutions.stream().collect(Collectors.toSet()),
                                                configuration.get())
                                        .takeWhile(s -> ctx.session.isOpen())
                                        .filter(solution -> !configuration.get().strict
                                                || previousSolutions.stream()
                                                        .noneMatch(other -> other.dominates(solution)))
                                        .forEach(s -> {
                                            previousSolutions.add(s);
                                            if (ctx.session.isOpen())
                                                ExplorationSolutionMessage.from(s).toJsonString().ifPresent(ctx::send);
                                            logger.debug("Sent a solution, total now: %s"
                                                    .formatted(previousSolutions.size()));
                                        });
                                logger.debug("Finished exploration");
                                if (ctx.session.isOpen())
                                    ctx.send("done");
                                // ctx.closeSession();
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
                                                                        .ifPresent(configuration::set)));
                            }
                        });
                        ws.onConnect(ctx -> {
                            logger.debug("A client connected to exploration");
                            ctx.enableAutomaticPings(1, TimeUnit.SECONDS);
                            explorers().stream()
                                    .filter(e -> e.uniqueIdentifier().equalsIgnoreCase(ctx.pathParam("explorerName")))
                                    .findAny()
                                    .ifPresentOrElse(explorer::set, ctx::closeSession);
                            previousSolutions.clear();
                        });
                        ws.onError(ctx -> {
                            logger.error("An error occurred during exploration");
                            ctx.error().printStackTrace();
                            if (ctx.session.isOpen()) {
                                ctx.send("done");
                                ctx.closeSession();
                            }
                            // ctx.send("ERROR " + ctx.error().getMessage());
                        });
                    })
                    // .ws("/reverse", ws -> {
                    // var logger = LoggerFactory.getLogger("main");
                    // Set<DecisionModel> exploredDecisionModels = new CopyOnWriteArraySet<>();
                    // Set<DesignModel> designModels = new CopyOnWriteArraySet<>();
                    // ws.onBinaryMessage(ctx -> {
                    // OpaqueDesignModel.fromCBORBytes(ctx.data()).flatMap(this::fromOpaqueDesign)
                    // .ifPresentOrElse(designModels::add,
                    // () -> OpaqueDecisionModel.fromCBORBytes(ctx.data())
                    // .flatMap(this::fromOpaqueDecision)
                    // .ifPresent(exploredDecisionModels::add));
                    // });
                    // ws.onMessage(ctx -> {
                    // if (ctx.message().toLowerCase().contains("done")) {
                    // logger.debug("Running a reverse identification with %s and %s decision and
                    // design models"
                    // .formatted(exploredDecisionModels.size(), designModels.size()));
                    // var reversed = reverseIdentification(exploredDecisionModels, designModels);
                    // for (var result : reversed) {
                    // OpaqueDesignModel.from(result).toJsonString().ifPresent(bytes -> {
                    // logger.debug("Sending a reverse identified design model");
                    // if (ctx.session.isOpen())
                    // ctx.send(bytes);
                    // // designModels.add(result);
                    // });
                    // }
                    // logger.debug(
                    // "Finished a reverse identification step with %s design models identified"
                    // .formatted(designModels.size()));
                    // if (ctx.session.isOpen())
                    // ctx.send("done");
                    // logger.debug("Sent the done request");
                    // ctx.closeSession();
                    // } else {
                    // OpaqueDesignModel.fromJsonString(ctx.message()).flatMap(this::fromOpaqueDesign)
                    // .ifPresentOrElse(
                    // designModels::add,
                    // () -> OpaqueDecisionModel.fromJsonString(ctx.message())
                    // .flatMap(this::fromOpaqueDecision)
                    // .ifPresent(exploredDecisionModels::add));
                    // }
                    // });
                    // ws.onConnect(ctx -> {
                    // logger.debug("A new reverse identification client connected");
                    // ctx.enableAutomaticPings(1, TimeUnit.SECONDS);
                    // exploredDecisionModels.clear();
                    // designModels.clear();
                    // });
                    // ws.onError(ctx -> {
                    // logger.error("An error occurred in the reverse identification websocket");
                    // if (ctx.session.isOpen()) {
                    // ctx.send("done");
                    // ctx.closeSession();
                    // }
                    // });
                    // })
                    .post("/reverse", ctx -> {
                        var logger = LoggerFactory.getLogger("main");
                        Set<DecisionModel> exploredDecisionModels = new HashSet<>();
                        Set<DesignModel> designModels = new HashSet<>();
                        cachedSolvedDecisionModels.values().forEach(exploredDecisionModels::add);
                        cachedDesignModels.values().forEach(designModels::add);
                        logger.debug("Running a reverse identification with %s and %s decision and design models"
                                .formatted(exploredDecisionModels.size(), designModels.size()));
                        // ctx.formParamMap().forEach((name, entries) -> {
                        // if (name.startsWith("solved")) {
                        // entries.stream().findAny().ifPresent(msg -> {
                        // OpaqueDecisionModel.fromJsonString(msg).flatMap(this::fromOpaqueDecision)
                        // .ifPresent(exploredDecisionModels::add);
                        // });
                        // } else if (name.startsWith("design")) {
                        // entries.stream().findAny().ifPresent(msg -> {
                        // OpaqueDesignModel.fromJsonString(msg).flatMap(this::fromOpaqueDesign)
                        // .ifPresent(designModels::add);
                        // });
                        // }
                        // });
                        var reversed = reverseIdentification(exploredDecisionModels, designModels);
                        var reverseResponse = new ArrayList<String>();
                        for (var result : reversed) {
                            result.globalSHA2Hash().ifPresent(hash -> {
                                cachedReversedDesignModels.put(ByteBuffer.wrap(hash), result);
                                reverseResponse.add(Base64.getEncoder().withoutPadding().encodeToString(hash));
                                // System.out.println(Arrays.toString(hash));
                            });
                        }
                        // ctx.result("[ %s ]".formatted(String.join(", ",
                        // reverseResponse.stream().map(s -> "\"" + s +
                        // "\"").collect(Collectors.toList()))));
                        ctx.json(reverseResponse);
                        logger.debug("Finished a reverse identification with %s design models reverse identified"
                                .formatted(reverseResponse.size()));
                        // if (ctx.isMultipart()) {
                        // }
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
                    })
                    .wsException(Exception.class, (e, ctx) -> {
                        e.printStackTrace();
                    })
                    .updateConfig(config -> {
                        config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.GB);
                        config.jetty.multipartConfig.maxFileSize(1, SizeUnit.GB);
                        config.jetty.wsFactoryConfig(cfg -> {
                            cfg.setMaxTextMessageSize(1000000000);
                            cfg.setMaxBinaryMessageSize(1000000000);
                        });
                        config.jetty.contextHandlerConfig(ctx -> {
                            ctx.setMaxFormContentSize(1000000000);
                        });
                        config.http.maxRequestSize = 1000000000;
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
