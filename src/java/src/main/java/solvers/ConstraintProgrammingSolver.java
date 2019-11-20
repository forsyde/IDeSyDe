package solvers;

import java.util.Optional;

import core.DecisionSet;
import core.Solver;

/**
 * @author rjordao
 *
 */
public class ConstraintProgrammingSolver implements Solver {

	@Override
	public Optional<DecisionSet> solve(DecisionSet dset) {
		return Optional.empty();
	}

	@Override
	public boolean solvable(DecisionSet dset) {
		return true;
	}

}
