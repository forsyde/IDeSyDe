/**
 * 
 */
package desyde.java.prolog.jpl;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jpl7.Atom;
import org.jpl7.Compound;
import org.jpl7.JPL;
import org.jpl7.Query;
import org.jpl7.Term;

import desyde.java.prolog.PrologEngine;

/**
 * @author rjordao
 *
 */
public class JPLEngine extends PrologEngine {

	
	public static JPLEngine getInstance() {
		if (engine == null) {
			engine = new JPLEngine();
			JPL.init();
		}
		return engine;
	}

	@Override
	public <T> Optional<Map<String, T>> singleQuery(Set<String> files, String query, Function<String, T> parser) {
		// Do the consultation of the initial files
		Term[] filesTerm = new Term[files.size()];
		Iterator<String> it = files.iterator();
		int i = 0;
		while (it.hasNext()) {
			filesTerm[i] = new Atom(it.next().toString());
			i++;
		}
		
		Query q = new Query(new Compound("consult", filesTerm));
		q.hasSolution();
		q.close();
		// now do the real query
		Query goal = new Query(Term.textToTerm(query));
		while(goal.hasMoreSolutions()) {
			System.out.println(goal.nextSolution());
		}
		return Optional.empty();
	}

	@Override
	public <T> Iterable<Map<String, T>> multiQuery(Set<String> files, String query, Function<String, T> parser) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private static JPLEngine engine;

}
