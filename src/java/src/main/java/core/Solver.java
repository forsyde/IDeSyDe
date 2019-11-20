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
  
	public Optional<DecisionSet> solve(DecisionSet dset);
	
	public boolean solvable(DecisionSet dset);
	
}
