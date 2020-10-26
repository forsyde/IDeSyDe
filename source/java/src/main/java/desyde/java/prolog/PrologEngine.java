/**
 * 
 */
package desyde.java.prolog;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * @author rjordao
 *
 */
public abstract class PrologEngine {
	
	public abstract <T> Optional<Map<String, T>> singleQuery(
			Set<String> filesPaths,
			String query,
			Function<String, T> parser
		);
	
	public abstract <T> Iterable<Map<String, T>> multiQuery(
			Set<String> filesPaths,
			String query,
			Function<String, T> parser
		);
}
