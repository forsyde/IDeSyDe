package idesyde.core;

import java.util.Set;
import java.util.function.BiFunction;

public interface IdentificationRule<T extends DecisionModel> extends
        BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, Set<T>> {

    default boolean usesDesignModels() { return true; }

    default boolean usesDecisionModels() { return true; }

    default Set<String> usesParticularDecisionModels() { return Set.of(); }
}
