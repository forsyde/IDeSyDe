/**
 * 
 */
package desyde.java.prolog.jpl;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jpl7.JPL;
import org.jpl7.Query;
import org.jpl7.Term;

import desyde.java.prolog.PrologEngine;

/**
 * @author rjordao
 *
 */
public class JPLEngine implements PrologEngine {

	
	public static JPLEngine getInstance() {
		if (engine == null) {
			engine = new JPLEngine();
			JPL.init();
		}
		return engine;
	}

	@Override
	public <T> Optional<Map<String, T>> singleQuery(Set<Path> files, String query, Function<String, T> parser) {
		System.out.println("consult([" + files.stream().map((p) -> "'" + p.toString() + "'").collect(Collectors.joining(", ")) + "]).");
		Query q = new Query(
				"consult([" + files.stream().map((p) -> "'" + p.toString() + "'").collect(Collectors.joining(", ")) + "])."
				);
		System.out.println(q.toString());
		q.hasSolution();
		q.close();
		//Term.textToTerm(query);
		return null;
	}

	@Override
	public <T> Iterable<Map<String, T>> multiQuery(Set<Path> files, String query, Function<String, T> parser) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private static JPLEngine engine;

}
