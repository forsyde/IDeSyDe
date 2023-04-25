package idesyde.core;

import java.util.Set;
import java.util.function.BiFunction;

public interface ReverseIdentificationRule<T extends DesignModel> extends
        BiFunction<Set<? extends DecisionModel>, Set<? extends DesignModel>, Set<T>> {
}
