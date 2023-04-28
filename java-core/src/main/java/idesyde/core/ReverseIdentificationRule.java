package idesyde.core;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * A class that represents reverse identification rules. It is not more than a function being cast into class form
 * so that every JVM language can handle it.
 *
 * @param <T> The DesignModel being returned.
 */
public interface ReverseIdentificationRule<T extends DesignModel> extends
        BiFunction<Set<? extends DecisionModel>, Set<? extends DesignModel>, Set<T>> {
}
