package idesyde.metaheuristics.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.jenetics.Gene;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;

public class MultiConstraint<A, G extends Gene<A, G>, T extends Comparable<? super T>> implements Constraint<G, T> {

    List<Constraint<G, T>> constraints = new ArrayList<>();

    @SafeVarargs
    public MultiConstraint(Constraint<G, T>... constraints) {
        this.constraints.addAll(Arrays.asList(constraints));
    }

    @Override
    public boolean test(Phenotype<G, T> individual) {
        for (Constraint<G, T> c : constraints) {
            if (!c.test(individual)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Phenotype<G, T> repair(Phenotype<G, T> individual, long generation) {
        var repaired = individual;
        for (Constraint<G, T> c : constraints) {
            if (!c.test(repaired)) {
                repaired = c.repair(repaired, generation);
            }
        }
        return repaired;
    }

}
