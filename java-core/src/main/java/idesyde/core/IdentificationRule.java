package idesyde.core;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * A class that represent an identification rule, including how it partially
 * identifies new DecisionModel s but also
 * whether it only uses DesignModel s, DecisionModel s or a combination.
 *
 */
public interface IdentificationRule extends
        BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, IdentificationResult> {

    default boolean usesDesignModels() {
        return true;
    }

    default boolean usesDecisionModels() {
        return true;
    }

    default Set<String> usesParticularDecisionModels() {
        return Set.of();
    }

    /**
     * A simple wrapper that signals the wrapper function requires only design
     * models.
     * 
     * @param func the wrapped function.
     */
    static record OnlyDesignModels(
            BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, IdentificationResult> func)
            implements IdentificationRule {

        @Override
        public IdentificationResult apply(Set<? extends DesignModel> designModels,
                Set<? extends DecisionModel> decisionModels) {
            return func.apply(designModels, decisionModels);
        }

        @Override
        public boolean usesDecisionModels() {
            return false;
        }
    }

    /**
     * A simple wrapper that signals the wrapper function requires only (any)
     * decision models.
     * 
     * @param func the wrapped function.
     */
    static record OnlyDecisionModels(
            BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, IdentificationResult> func)
            implements IdentificationRule {

        @Override
        public IdentificationResult apply(Set<? extends DesignModel> designModels,
                Set<? extends DecisionModel> decisionModels) {
            return func.apply(designModels, decisionModels);
        }

        @Override
        public boolean usesDesignModels() {
            return false;
        }
    }

    /**
     * A simple wrapper that signals the wrapper function requires only certain
     * decision models.
     * 
     * @param func the wrapped function.
     */
    static record OnlyCertainDecisionModels(
            BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, IdentificationResult> func,
            Set<String> decisionModelCategories) implements IdentificationRule {

        @Override
        public IdentificationResult apply(Set<? extends DesignModel> designModels,
                Set<? extends DecisionModel> decisionModels) {
            return func.apply(designModels, decisionModels);
        }

        @Override
        public boolean usesDesignModels() {
            return false;
        }

        @Override
        public Set<String> usesParticularDecisionModels() {
            return decisionModelCategories;
        }
    }

    /**
     * A simple wrapper for a function that can identify decision models.
     * 
     * @param func the wrapped function.
     */
    static record Generic(
            BiFunction<Set<? extends DesignModel>, Set<? extends DecisionModel>, IdentificationResult> func,
            Set<String> decisionModelCategories) implements IdentificationRule {
        @Override
        public IdentificationResult apply(Set<? extends DesignModel> designModels,
                Set<? extends DecisionModel> decisionModels) {
            return func.apply(designModels, decisionModels);
        }
    }
}
