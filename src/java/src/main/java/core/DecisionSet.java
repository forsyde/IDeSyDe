/**
 * 
 */
package core;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author rjordao
 *
 */
public class DecisionSet {

	Set<String> variables;
	Map<String, ?> decisions;
	Set<String> constraints;
	Set<String> goals;
	
	public Set<String> freeDecisions() {
		return variables.stream()
				.filter((v) -> !decisions.keySet().contains(v))
				.collect(Collectors.toSet());
	};
}
