package idesyde.core;

import idesyde.core.headers.ExplorationBidding;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This trait is the root for all possible explorers within IDeSyDe. A real explorer should
 * implement this trait by dispatching the real exploration from 'explore'.
 * <p>
 * The Design model is left a type parameter because the explorer might be used in a context where
 * explorers for different decision models and design models are used together. A correct
 * implemention of the explorer should then:
 * <p>
 * 1. if the DesignModel is part of the possible design models covered, it should return a
 * lazylist accodingly.
 * 2. If the DesignModel si not part of the possible design models, then
 * the explorer should return an empty lazy list.
 * 3. If the decision model is unexplorable regardless, an empty list should be returned.
 * <p>
 * See [1] for some extra information on how the explorer fits the design space identifcation
 * approach, as well as [[idesyde.exploration.api.ExplorationHandler]] to see how explorers used
 * together in a generic context.
 * <p>
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 */
public interface Explorer {

    default ExplorationBidding bid(DecisionModel decisionModel) {
        return new ExplorationBidding(uniqueIdentifer(), decisionModel.header().category(), false, Map.of());
    }

    default Stream<? extends ExplorationSolution> explore(DecisionModel decisionModel,
                                                  Set<Map<String, Double>> objectivesUpperLimits,
                                                  Configuration configuration) {
        return Stream.empty();
    }

    default String uniqueIdentifer() {
        return getClass().getSimpleName();
    }

    record Configuration(
            long totalExplorationTimeOutInSecs,
            long maximumSolutions,
            long timeDiscretizationFactor,
            long memoryDiscretizationFactor
    ) {
        static public Configuration unlimited() {
            return new Configuration(0L, 0L, 0L, 0L);
        }
    }
}
