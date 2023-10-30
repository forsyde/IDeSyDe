package idesyde.metaheuristics.constraints;

import java.util.ArrayList;
import java.util.List;

import io.jenetics.Gene;
import io.jenetics.IntegerGene;
import io.jenetics.Phenotype;
import io.jenetics.engine.Constraint;

public class MultiConstraint<A, G extends Gene<A, G>, T extends Comparable<? super T>> implements Constraint<G, T> {

    List<Constraint<G, T>> constraints = new ArrayList<>();

    public MultiConstraint(Constraint<G, T>... constraints) {
        for (Constraint<G, T> c : constraints) {
            this.constraints.add(c);
        }
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
        for (Constraint<G, T> c : constraints) {
            if (!c.test(individual)) {
                return c.repair(individual, generation);
            }
        }
        return individual;
    }

}
