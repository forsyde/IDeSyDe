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
public interface PrologEngine {
	
	public <T> Optional<Map<String, T>> singleQuery(
			Set<Path> files,
			String query,
			Function<String, T> parser
		);
	
	public <T> Iterable<Map<String, T>> multiQuery(
			Set<Path> files,
			String query,
			Function<String, T> parser
		);
}
