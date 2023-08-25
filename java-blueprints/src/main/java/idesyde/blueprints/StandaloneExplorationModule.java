package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.DecisionModel;
import idesyde.core.Explorer;
import io.javalin.Javalin;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public interface StandaloneExplorationModule {

    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
    static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    Optional<DecisionModel> decisionMessageToModel(DecisionModelMessage message);

    Set<Explorer> explorers();

    default Optional<Javalin> standaloneExplorationModuleServer(
            String[] args
    ) {
        var cli = new ExplorationModuleCLI();
        var res = new CommandLine(cli).execute(args);
        if (res == 0) {
            if (cli.serverType != null && cli.serverType.equalsIgnoreCase("http")) {
                var decisionModels = new HashSet<DecisionModel>();
                var solvedDecisionModels = new HashSet<DecisionModel>();
                try (var server = Javalin.create()) {
                    server.post(
                                    "/set",
                                    ctx -> {
                                        if (ctx.queryParamMap().containsKey("parameter")) {}
                                    }
                            )
                            .post(
                                    "/decision",
                                    ctx ->
                                            DecisionModelMessage.fromJsonString(ctx.body())
                                                    .flatMap(this::decisionMessageToModel)
                                                    .ifPresent(decisionModels::add)
                            )
                            .post(
                                    "/solved",
                                    ctx -> DecisionModelMessage.fromJsonString(ctx.body())
                                            .flatMap(this::decisionMessageToModel)
                                            .ifPresent(solvedDecisionModels::add)
                            )
                            .get(
                                    "/bid",
                                    ctx -> {
                                        var bids = DecisionModelMessage.fromJsonString(ctx.body())
                                                .flatMap(this::decisionMessageToModel)
                                                .map(decisionModel ->
                                                        explorers().stream().map(explorer ->
                                                                        explorer.bid(decisionModel)
                                                                )
                                                                .collect(Collectors.toList())
                                                ).orElse(List.of());
                                        ctx.result(objectMapper.writeValueAsString(bids));
                                    }
                            )
                            .ws(
                                    "/explore",
                                    ws -> {
                                        ws.onMessage(ctx -> {
                                            var request = objectMapper.readValue(ctx.message(), ExplorationRequestMessage.class);
                                            explorers().stream().filter(e -> e.uniqueIdentifer().equals(request.explorerIdentifier())).findAny().ifPresent(explorer -> {
                                                decisionMessageToModel(request.modelMessage()).ifPresent(decisionModel -> {
                                                    explorer.explore(decisionModel, request.previousSolutions(), Explorer.Configuration.unlimited()).forEach(solution -> {
                                                        try {
                                                            ctx.send(objectMapper.writeValueAsString(ExplorationSolutionMessage.from(solution)));
                                                        } catch (JsonProcessingException e) {
                                                            e.printStackTrace();
                                                        }
                                                    });
                                                });
                                            });
                                            ctx.closeSession();
                                        });
                                    }
                            )
                            .exception(
                                    Exception.class,
                                    (e, ctx) -> {
                                        e.printStackTrace();
                                    }
                            );
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
        }
        return Optional.empty();
    }

    @CommandLine.Command(mixinStandardHelpOptions = true)
    class ExplorationModuleCLI implements Callable<Integer> {
        @CommandLine.Option(names = {"--server"}, description = "The type of server to start.")
        String serverType;

        @Override
        public Integer call() throws Exception {
            return 0;
        }
    }

    default<T extends DecisionModel> Optional<T> readDecisionModel(String jsonString, Class<T> cls) {
        try {
            return Optional.of(objectMapper.readValue(jsonString, cls));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    };
}
