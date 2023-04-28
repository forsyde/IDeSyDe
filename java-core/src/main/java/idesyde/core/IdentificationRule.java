package idesyde.core;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * A class that represent an identification rule, including how it partially identifies new DecisionModel s but also
 * whether it only uses DesignModel s, DecisionModel s or a combination.
 *
 * @param <T> The DecisionModel that is generated.
 */
public interface IdentificationRule<T extends DecisionModel> extends
        BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, Set<T>> {

    default boolean usesDesignModels() { return true; }

    default boolean usesDecisionModels() { return true; }

    default Set<String> usesParticularDecisionModels() { return Set.of(); }
}
