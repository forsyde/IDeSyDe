package idesyde.core;

import idesyde.core.headers.ExplorationBidding;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This trait is the root for all possible explorers within IDeSyDe. A real
 * explorer should
 * implement this trait by dispatching the real exploration from 'explore'.
 * <p>
 * The Design model is left a type parameter because the explorer might be used
 * in a context where
 * explorers for different decision models and design models are used together.
 * A correct
 * implemention of the explorer should then:
 * <p>
 * 1. if the DesignModel is part of the possible design models covered, it
 * should return a
 * lazylist accodingly.
 * 2. If the DesignModel si not part of the possible design models, then
 * the explorer should return an empty lazy list.
 * 3. If the decision model is unexplorable regardless, an empty list should be
 * returned.
 * <p>
 * See [1] for some extra information on how the explorer fits the design space
 * identifcation
 * approach, as well as [[idesyde.exploration.api.ExplorationHandler]] to see
 * how explorers used
 * together in a generic context.
 * <p>
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space
 * Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test
 * in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 */
public interface Explorer {

    default ExplorationBidding bid(DecisionModel decisionModel) {
        return new ExplorationBidding(uniqueIdentifier(), false, false, 10.0, Set.of(), Map.of());
    }

    default Stream<? extends ExplorationSolution> explore(DecisionModel decisionModel,
            Set<ExplorationSolution> previousSolutions,
            Configuration configuration) {
        return Stream.empty();
    }

    default String uniqueIdentifier() {
        return getClass().getSimpleName();
    }

    class Configuration {

        @JsonProperty("total_timeout")
        public long totalExplorationTimeOutInSecs;
        @JsonProperty("max_sols")
        public long maximumSolutions;
        @JsonProperty("time_resolution")
        public long timeDiscretizationFactor;
        @JsonProperty("memory_resolution")
        public long memoryDiscretizationFactor;

        public Configuration(long totalExplorationTimeOutInSecs, long maximumSolutions, long timeDiscretizationFactor,
                long memoryDiscretizationFactor) {
            this.totalExplorationTimeOutInSecs = totalExplorationTimeOutInSecs;
            this.maximumSolutions = maximumSolutions;
            this.timeDiscretizationFactor = timeDiscretizationFactor;
            this.memoryDiscretizationFactor = memoryDiscretizationFactor;
        }

        static public Configuration unlimited() {
            return new Configuration(0L, 0L, 0L, 0L);
        }

        public Configuration withTotalExplorationTimeOutInSecs(long newTotalExplorationTimeOutInSecs) {
            return new Configuration(newTotalExplorationTimeOutInSecs, maximumSolutions, timeDiscretizationFactor,
                    memoryDiscretizationFactor);
        }

        public Configuration withMaximumSolutions(long newMaximumSolutions) {
            return new Configuration(totalExplorationTimeOutInSecs, newMaximumSolutions, timeDiscretizationFactor,
                    memoryDiscretizationFactor);
        }

        public Configuration withTimeDiscretizationFactor(long newTimeDiscretizationFactor) {
            return new Configuration(totalExplorationTimeOutInSecs, maximumSolutions, newTimeDiscretizationFactor,
                    memoryDiscretizationFactor);
        }

        public Configuration withMemoryDiscretizationFactor(long newMemoryDiscretizationFactor) {
            return new Configuration(totalExplorationTimeOutInSecs, maximumSolutions, timeDiscretizationFactor,
                    newMemoryDiscretizationFactor);
        }
    }
}
