package desyde.preprocessing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ForSyDe.Model.Application.CompoundProcess;
import ForSyDe.Model.Core.Definition;
import ForSyDe.Model.Core.Edge;
import ForSyDe.Model.Core.Graph;
import ForSyDe.Model.Core.Identifiable;
import ForSyDe.Model.Core.Port;
import ForSyDe.Model.Core.Vertex;
import ForSyDe.Model.IO.ForSyDeIO;

public class Unroller {
	
	ForSyDeIO model;
	ForSyDeIO flattenedModel;
	
	public ForSyDeIO unrollModel(ForSyDeIO model) {
		if (this.model != model || flattenedModel == null) {
			flattenedModel = new ForSyDeIO();	
			unroll(model);
		}
		this.model = model;
		return flattenedModel;
	}
	
	protected void unroll(ForSyDeIO model) {
		Set<Vertex> vertexes = model.streamContained()
				.filter(v -> v instanceof Vertex)
				.map(v -> (Vertex) v)
				.collect(Collectors.toSet());
		Set<Edge> edges = model.streamContained()
				.filter(e -> e instanceof Edge)
				.map(e -> (Edge) e)
				.collect(Collectors.toSet());
		Set<Definition> defs = model.streamContained()
				.filter(v -> v instanceof Definition)
				.map(v -> (Definition) v)
				.collect(Collectors.toSet());
		// count via Dprog number of copies
		Map<Definition, Integer> numCopies = countNumberOfCopies(model);
		// create all vertexes, ports and definition copies
		Map<Vertex, HashSet<Vertex>> vertexCopies = new HashMap<>();
		Map<Port, HashSet<Port>> portsCopies = new HashMap<>();
		Map<Definition, HashSet<Definition>> defsCopies = new HashMap<>();
		Map<Edge, HashSet<Edge>> edgesCopies = new HashMap<>();
		// topmost entities, the vertexes are guaranteed to be simple
		model.streamContained().filter(e -> e instanceof Vertex).map(e -> (Vertex) e).forEach(v -> {
			HashSet<Vertex> set = new HashSet<>();
			set.add(v.containedCopy());
			vertexCopies.put(v, set);
		});
		// create all copies by iterating the definitions
		for(Definition d : defs) {
			d.streamContained().filter(e -> e instanceof Vertex).map(e -> (Vertex) e)
			.forEach(vert -> {
				// vertexes
				if (numCopies.get(d) > 1) {
					HashSet<Vertex> set = new HashSet<>();
					for (int i = 1; i <= numCopies.get(d); i++) {
						Vertex copy = vert.containedCopy();
						copy.identifier += '/' + String.valueOf(i);
						set.add(copy);
					}
					vertexCopies.put(vert, set);
				} else {
					HashSet<Vertex> set = new HashSet<>();
					set.add(vert.containedCopy());
					vertexCopies.put(vert, set);
				}
			});
			d.streamContained().filter(e -> e instanceof Port).map(p -> (Port) p)
			.forEach(p -> {
				// ports of vertexes
				if (numCopies.get(d) > 1) {
					HashSet<Port> set = new HashSet<>();
					for (int i = 1; i <= numCopies.get(d); i++) {
						Port copy = p.containedCopy();
						copy.identifier += '/' + String.valueOf(i);
						set.add(copy);	
					}
					portsCopies.put(p, set);
				} else {
					HashSet<Port> set = new HashSet<>();
					set.add(p.containedCopy());
					portsCopies.put(p, set);
				}	
			});
			// definitions
			if (numCopies.get(d) > 1) {
				HashSet<Definition> set = new HashSet<>();
				for(int i = 1; i <= numCopies.get(d); i++) {
					Definition copy = d.containedCopy();
					copy.identifier += '/' + String.valueOf(i);
					set.add(copy);
				}
				defsCopies.put(d, set);
			} else {
				HashSet<Definition> set = new HashSet<>();
				set.add(d.containedCopy());
				defsCopies.put(d, set);
			}
			// edges
			d.streamContained().filter(e -> e instanceof Edge).map(e -> (Edge) e).forEach(e -> {
				if (numCopies.get(d) > 1) {
					HashSet<Edge> set = new HashSet<>();
					for(int i = 1; i <= numCopies.get(d); i++) {
						Edge copy = e.containedCopy();
						copy.identifier += '/' + String.valueOf(i);
						set.add(copy);
					}
					edgesCopies.put(e, set);
				} else {
					HashSet<Edge> set = new HashSet<>();
					set.add(e.containedCopy());
					edgesCopies.put(e, set);
				}
			});
		}
		// initiate re-referencing stage
		HashSet<Identifiable> visited = new HashSet<>();
		// first, make all vertexes point to their new Clones
		for (Vertex v : vertexes) {
			for (Vertex copy : vertexCopies.get(v)) {
				Definition d = defsCopies.get(v.definition).stream()
						.filter(de -> !visited.contains(de))
						.findAny().get();
				copy.definition = d;
				copy.inPorts.clear();
				copy.outPorts.clear();
				for (Port p : v.inPorts) {
					Port pCopy = portsCopies.get(p).stream()
							.filter(e -> !visited.contains(e))
							.findAny().get();
					copy.inPorts.add(pCopy);
					visited.add(pCopy);
				}
				for (Port p : v.outPorts) {
					Port pCopy = portsCopies.get(p).stream()
							.filter(e -> !visited.contains(e))
							.findAny().get();
					copy.outPorts.add(pCopy);
					visited.add(pCopy);
				}
				visited.add(d);
			}
		}
		// then for the ports
		visited.clear();
		vertexes.stream().flatMap(v -> Stream.concat(v.inPorts.stream(), v.outPorts.stream()))
		.filter(p -> p.definer != null)
		.forEach(p -> {
			for (Port copy : portsCopies.get(p)) {
				Port definerCopy = portsCopies.get(p.definer).stream()
						.filter(pc -> !visited.contains(pc))
						.findAny().get();
				copy.definer = definerCopy;
			}			
			visited.add(p);
		});
		// now put everything together nesting wise
		visited.clear();
		for (Definition d : defs) {
			for (Definition copy : defsCopies.get(d)) {
				// takes care of recreating the graphs when they are children
				// assumes there will always be only one graph contained
				Optional<Identifiable> gOpt = d.streamContained().filter(e -> e instanceof Graph).findAny();
				if (gOpt.isPresent()) {
					Graph g = (Graph) gOpt.get();
					Graph gCopy = (Graph) copy.streamContained().filter(e -> e instanceof Graph).findAny().get(); 
					gCopy.vertexes.clear();
					gCopy.edges.clear();
					for (Vertex v : g.vertexes) {
						Vertex vCopy = vertexCopies.get(v).stream()
								.filter(ver -> !visited.contains(ver))
								.findAny().get();
						gCopy.vertexes.add(vCopy);
						visited.add(vCopy);
						// including ports
						for(Port p : v.inPorts) {
							Port pCopy = portsCopies.get(p).stream()
									.filter(ver -> !visited.contains(ver))
									.findAny().get();
							vCopy.inPorts.add(pCopy);
						}
					}
					for (Edge e : g.edges) {
						Edge eCopy = edgesCopies.get(e).stream()
								.filter(ver -> !visited.contains(ver))
								.findAny().get();
						gCopy.edges.add(eCopy);
						visited.add(eCopy);
					}
				}
				// finish by adding the definition
				flattenedModel.definitions.add(copy);
			}
		}
	
		// add topmost vertexes to model
		for (Vertex v : model.applications) {
			Vertex vCopy = vertexCopies.get(v).iterator().next();
			flattenedModel.applications.add(vCopy);
			vertexCopies.remove(vCopy);
		}
		for (Vertex v : model.platforms) {
			Vertex vCopy = vertexCopies.get(v).iterator().next();
			flattenedModel.platforms.add(vCopy);
			vertexCopies.remove(vCopy);
		}
		for (Vertex v : model.refinement) {
			Vertex vCopy = vertexCopies.get(v).iterator().next();
			flattenedModel.refinement.add(vCopy);
			vertexCopies.remove(vCopy);
		}
	}
	
	private Map<Definition, Integer> countNumberOfCopies(ForSyDeIO model) {
		Set<Vertex> vertexes = model.streamContained()
				.filter(v -> v instanceof Vertex)
				.map(v -> (Vertex) v)
				.collect(Collectors.toSet());
		Set<Definition> defs = model.streamContained()
				.filter(v -> v instanceof Definition)
				.map(v -> (Definition) v)
				.collect(Collectors.toSet());
		Map<Definition, Integer> numCopies = defs.stream().collect(Collectors.toMap(d -> d, d -> 0));
		// make the dependency graph
		Map<Definition, Set<Definition>> referenced = defs.stream()
				.collect(Collectors.toMap(d -> d, d -> new HashSet<>()));
		// add all minimal copies for all definitions by making a sort of dependency graph
		for (Vertex v : vertexes) {
			v.definition.streamContained()
			.flatMap(d -> d.getReferences())
			.filter(d -> d instanceof Definition)
			.forEach(ref -> {
				numCopies.put((Definition) ref, numCopies.get(ref) + 1);
				referenced.get(ref).add(v.definition);
			});	
		}
		// if a definition has 0 copies, it means that it is a top entity, so bump it to 1,
		for (Definition def : defs) {
			if (numCopies.get(def) == 0) {
				numCopies.put(def, 1);
			}
		}
		// due to dynamic programming principles, you need to execute this V*(V-1) times.
		for(int i = 0; i < defs.size(); i++) {
			for (Definition d : defs) {
				Integer candidate = referenced.get(d).stream()
						.map(par -> numCopies.get(par))
						.reduce((a,b) -> a + b).orElse(1);
				if (numCopies.get(d) < candidate) {
					numCopies.put(d, candidate);
				}
			}
		}
		return numCopies;
	}

}
