package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.DecisionModel;
import idesyde.core.ExplorationSolution;
import idesyde.core.Explorer;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface StandaloneExplorationModule {

    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
    static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message);

    Set<Explorer> explorers();

    default Optional<Javalin> standaloneExplorationModuleServer(
            ExplorationModuleCLI cli) {
        if (cli.serverType != null && cli.serverType.equalsIgnoreCase("http")) {
            var decisionModels = new HashSet<DecisionModel>();
            try (var server = Javalin.create()) {
                server
                        // .post(
                        // "/set",
                        // ctx -> {
                        // if (ctx.queryParamMap().containsKey("parameter")) {
                        // switch (ctx.queryParam("parameter").toLowerCase()) {
                        // case "maximum-solutions":
                        // case "max-sols":
                        // configuration.maximumSolutions = Long.parseLong(ctx.body());
                        // break;
                        // case "total-timeout":
                        // configuration.totalExplorationTimeOutInSecs = Long.parseLong(ctx.body());
                        // break;
                        // case "time-resolution":
                        // case "time-res":
                        // configuration.timeDiscretizationFactor = Long.parseLong(ctx.body());
                        // break;
                        // case "memory-resolution":
                        // case "memory-res":
                        // case "mem-res":
                        // configuration.memoryDiscretizationFactor = Long.parseLong(ctx.body());
                        // break;
                        // default:
                        // break;
                        // }
                        // }
                        // })
                        .post(
                                "/decision",
                                ctx -> DecisionModelMessage.fromJsonString(ctx.body())
                                        .flatMap(this::decisionMessageToModel)
                                        .ifPresent(decisionModels::add))
                        .get(
                                "/explorers",
                                ctx -> {
                                    ctx.result(objectMapper.writeValueAsString(explorers().stream()
                                            .map(e -> e.uniqueIdentifier()).collect(Collectors.toSet())));
                                })
                        .get(
                                "/bid",
                                ctx -> {
                                    var bids = DecisionModelMessage.fromJsonString(ctx.body())
                                            .flatMap(this::decisionMessageToModel)
                                            .map(decisionModel -> explorers().stream()
                                                    .map(explorer -> explorer.bid(decisionModel))
                                                    .collect(Collectors.toList()))
                                            .orElse(List.of());
                                    ctx.result(objectMapper.writeValueAsString(bids));
                                })
                        .get(
                                "/{explorerName}/bid",
                                ctx -> {
                                    explorers().stream()
                                            .filter(e -> e.uniqueIdentifier().equals(ctx.pathParam("explorerName")))
                                            .findAny().ifPresentOrElse(explorer -> {
                                                DecisionModelMessage.fromJsonString(ctx.body())
                                                        .flatMap(this::decisionMessageToModel)
                                                        .map(decisionModel -> explorer.bid(decisionModel))
                                                        .ifPresent(bid -> {
                                                            try {
                                                                ctx.result(objectMapper.writeValueAsString(bid));
                                                            } catch (JsonProcessingException e1) {
                                                                e1.printStackTrace();
                                                                ctx.status(500);
                                                            }
                                                        });
                                            }, () -> {
                                                ctx.status(404);
                                            });
                                })
                        .ws(
                                "/{explorerName}/explore",
                                ws -> {
                                    // var solutions = new Concurrent<DecisionModel>();
                                    var explorationRequest = new ExplorationRequest();
                                    ws.onMessage(ctx -> {
                                        ctx.enableAutomaticPings(5, TimeUnit.SECONDS);
                                        // check whether it is a configuration, a solution, or a the decision model
                                        try {
                                            var prevSol = objectMapper.readValue(ctx.message(),
                                                    ExplorationSolutionMessage.class);
                                            decisionMessageToModel(prevSol.solved()).ifPresent((solution) -> {
                                                // solutions.add(solution);
                                                explorationRequest.previousSolutions
                                                        .add(new ExplorationSolution(prevSol.objectives(), solution));
                                            });
                                        } catch (DatabindException ignored) {
                                        }
                                        try {
                                            explorationRequest.configuration = objectMapper.readValue(ctx.message(),
                                                    Explorer.Configuration.class);
                                        } catch (DatabindException ignored) {
                                        }
                                        try {
                                            var request = objectMapper.readValue(ctx.message(),
                                                    DecisionModelMessage.class);
                                            explorers().stream().filter(
                                                    e -> e.uniqueIdentifier().equals(ctx.pathParam("explorerName")))
                                                    .findAny().ifPresent(explorer -> {
                                                        decisionMessageToModel(request)
                                                                .ifPresent(decisionModel -> {
                                                                    explorer.explore(decisionModel,
                                                                            explorationRequest.previousSolutions,
                                                                            explorationRequest.configuration)
                                                                            // keep only non dominated if necessary
                                                                            .filter(solution -> !explorationRequest.configuration.strict
                                                                                    || explorationRequest.previousSolutions
                                                                                            .stream()
                                                                                            .noneMatch(other -> other
                                                                                                    .dominates(
                                                                                                            solution)))
                                                                            .forEach(solution -> {
                                                                                try {
                                                                                    // solutions.add(solution.solved());
                                                                                    // explorationRequest.previousSolutions
                                                                                    // .add(solution);
                                                                                    ctx.send(objectMapper
                                                                                            .writeValueAsString(
                                                                                                    ExplorationSolutionMessage
                                                                                                            .from(solution)));
                                                                                } catch (JsonProcessingException e) {
                                                                                    e.printStackTrace();
                                                                                }
                                                                            });
                                                                });
                                                    });
                                            ctx.closeSession();
                                        } catch (DatabindException ignored) {
                                        } catch (IOException ignored) {
                                            System.out.println("Client closed channel during execution.");
                                        }
                                    });
                                })
                        .exception(
                                Exception.class,
                                (e, ctx) -> {
                                    if (e instanceof ClosedChannelException) {
                                        System.out.println("Client closed channel during execution.");
                                    } else {
                                        e.printStackTrace();
                                    }
                                })
                        .updateConfig(config -> {
                            config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.GB);
                            config.jetty.contextHandlerConfig(ctx -> {
                                ctx.setMaxFormContentSize(100000000);
                            });
                            config.jetty.wsFactoryConfig(wsconfig -> {
                                wsconfig.setMaxTextMessageSize(1000000000);
                            });
                            config.http.maxRequestSize = 1000000000;
                        });
                server.events(es -> {
                    es.serverStarted(() -> {
                        System.out.println("INITIALIZED " + server.port());
                    });
                });
                return Optional.of(server);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    static class ExplorationModuleCLI implements Callable<Integer> {
        @CommandLine.Option(names = { "--server" }, description = "The type of server to start.")
        public String serverType = "http";

        @Override
        public Integer call() throws Exception {
            return 0;
        }

    }

    default <T extends DecisionModel> Optional<T> readDecisionModel(String jsonString, Class<T> cls) {
        try {
            return Optional.of(objectMapper.readValue(jsonString, cls));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    };
}
