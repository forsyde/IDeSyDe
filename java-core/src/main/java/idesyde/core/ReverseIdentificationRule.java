package idesyde.core;

import java.util.Arrays;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * A class that represents reverse identification rules. It is not more than a
 * function being cast into class form
 * so that every JVM language can handle it.
 *
 */
public interface ReverseIdentificationRule extends
                BiFunction<Set<? extends DecisionModel>, Set<? extends DesignModel>, Set<? extends DesignModel>> {

        default Set<? extends DesignModel> fromArrays(DecisionModel[] decisionModels, DesignModel[] designModels) {
                return this.apply(Arrays.stream(decisionModels).collect(Collectors.toSet()), Arrays.stream(designModels).collect(Collectors.toSet()));
        }

        /**
         * A simple wrapper for a function that satisfies the proper reverse
         * identification rule signature.
         */
        record Generic(BiFunction<Set<? extends DecisionModel>, Set<? extends DesignModel>, Set<? extends DesignModel>> func)
                        implements ReverseIdentificationRule {

                @Override
                public Set<? extends DesignModel> apply(Set<? extends DecisionModel> t, Set<? extends DesignModel> u) {
                        return func().apply(t, u);
                }

        }
}
