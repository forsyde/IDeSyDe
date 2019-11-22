/**
 * 
 */
package core;

import java.util.Optional;

/**
 * @author rjordao
 *
 */
public interface Solver {
  
	Optional<DecisionSet> solve(DecisionSet dset);
	
	boolean solvable(DecisionSet dset);
	
}
