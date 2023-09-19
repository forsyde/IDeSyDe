package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.DecisionModel;
import idesyde.core.Explorer;
import io.javalin.Javalin;
import picocli.CommandLine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            var configuration = Explorer.Configuration.unlimited();
            var decisionModels = new HashSet<DecisionModel>();
            try (var server = Javalin.create()) {
                server.post(
                        "/set",
                        ctx -> {
                            if (ctx.queryParamMap().containsKey("parameter")) {
                                switch (ctx.queryParam("parameter").toLowerCase()) {
                                    case "maximum-solutions":
                                    case "max-sols":
                                        configuration.maximumSolutions = Long.parseLong(ctx.body());
                                        break;
                                    case "total-timeout":
                                        configuration.totalExplorationTimeOutInSecs = Long.parseLong(ctx.body());
                                        break;
                                    case "time-resolution":
                                    case "time-res":
                                        configuration.timeDiscretizationFactor = Long.parseLong(ctx.body());
                                        break;
                                    case "memory-resolution":
                                    case "memory-res":
                                    case "mem-res":
                                        configuration.memoryDiscretizationFactor = Long.parseLong(ctx.body());
                                        break;
                                    default:
                                        break;
                                }
                            }
                        })
                        .post(
                                "/decision",
                                ctx -> DecisionModelMessage.fromJsonString(ctx.body())
                                        .flatMap(this::decisionMessageToModel)
                                        .ifPresent(decisionModels::add))
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
                        .ws(
                                "/explore",
                                ws -> {
                                    ws.onMessage(ctx -> {
                                        ctx.enableAutomaticPings(5, TimeUnit.SECONDS);
                                        var found = new HashSet<DecisionModel>();
                                        var foundObjs = new HashMap<DecisionModel, Map<String, Double>>();
                                        var request = objectMapper.readValue(ctx.message(),
                                                ExplorationRequestMessage.class);
                                        explorers().stream().filter(
                                                e -> e.uniqueIdentifer().equals(request.explorerIdentifier()))
                                                .findAny().ifPresent(explorer -> {
                                                    decisionMessageToModel(request.modelMessage())
                                                            .ifPresent(decisionModel -> {
                                                                explorer.explore(decisionModel,
                                                                        request.previousSolutions(),
                                                                        configuration)
                                                                        // keep only non dominated
                                                                        .filter(solution -> foundObjs.values()
                                                                                .stream()
                                                                                .allMatch(other -> solution
                                                                                        .objectives().entrySet()
                                                                                        .stream()
                                                                                        .anyMatch(
                                                                                                objEntry -> objEntry
                                                                                                        .getValue() < other
                                                                                                                .get(objEntry
                                                                                                                        .getKey()))))
                                                                        .forEach(solution -> {
                                                                            try {
                                                                                found.add(solution.solved());
                                                                                foundObjs.put(solution.solved(),
                                                                                        solution.objectives());
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
                                    });
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
            }
        }
        return Optional.empty();
    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    class ExplorationModuleCLI implements Callable<Integer> {
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
