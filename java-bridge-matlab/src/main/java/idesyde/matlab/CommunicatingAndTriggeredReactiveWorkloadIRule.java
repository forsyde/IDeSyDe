package idesyde.matlab;

import idesyde.common.CommunicatingAndTriggeredReactiveWorkload;
import idesyde.core.DecisionModel;
import idesyde.core.DesignModel;
import idesyde.core.IdentificationRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommunicatingAndTriggeredReactiveWorkloadIRule implements IdentificationRule<CommunicatingAndTriggeredReactiveWorkload> {

    @Override
    public Set<CommunicatingAndTriggeredReactiveWorkload> apply(Set<? extends DesignModel> designModels, Set<? extends DecisionModel> decisionModels) {
        var simulinks = designModels.stream().filter(it -> it instanceof SimulinkReactiveDesignModel).map(it -> (SimulinkReactiveDesignModel) it).collect(Collectors.toSet());
        return Set.of(
                new CommunicatingAndTriggeredReactiveWorkload(
                        Stream.concat(simulinks.stream().flatMap(it -> it.processes().stream()), simulinks.stream().flatMap(it -> it.delays().stream())).collect(Collectors.toList()),
                        Stream.concat(simulinks.stream().flatMap(it -> it.processes().stream().map(p -> it.processesSizes().getOrDefault(p, 0L))), simulinks.stream().flatMap(it -> it.delays().stream().map(d -> it.delaysSizes().getOrDefault(d, 0L)))).collect(Collectors.toList()),
                        Stream.concat(simulinks.stream().flatMap(it -> it.processes().stream().map(p -> it.processesOperations().getOrDefault(p, Map.of()))), simulinks.stream().flatMap(it -> it.delays().stream().map(d -> it.delaysOperations().getOrDefault(d, Map.of())))).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.toString()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.dataSize()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.src()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.dst()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.dataSize()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.sources().stream()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.sources().stream().map(s -> it.sourcesPeriods().getOrDefault(s, 0.0))).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.sources().stream().map(s -> 0.0)).collect(Collectors.toList()),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.src()).collect(Collectors.toList()),
                        simulinks.stream().flatMap(it -> it.links().stream()).map(it -> it.dst()).collect(Collectors.toList()),
                        new HashSet<>()
                )
        );
    }
}
