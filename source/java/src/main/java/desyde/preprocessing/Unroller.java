package desyde.preprocessing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ForSyDe.Model.Application.CompoundProcess;
import ForSyDe.Model.Core.Definition;
import ForSyDe.Model.Core.Edge;
import ForSyDe.Model.Core.Identifiable;
import ForSyDe.Model.Core.Port;
import ForSyDe.Model.Core.Vertex;
import ForSyDe.Model.IO.ForSyDeIO;

public class Unroller {
	
	ForSyDeIO model;
	ForSyDeIO flattenedModel;
	
	Map<Vertex, HashSet<Vertex>> vertexCopies = new HashMap<>();
	Map<Port, HashSet<Port>> portsCopies = new HashMap<>();
	Map<Definition, HashSet<Definition>> defsCopies = new HashMap<>();
	Map<Edge, HashSet<Edge>> edgesCopies = new HashMap<>();
	Set<Vertex> vertexes = new HashSet<>();
	Set<Edge> edges = new HashSet<>();
	Set<Definition> defs = new HashSet<>();
	
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
		vertexCopies.clear();
		portsCopies.clear();
		defsCopies.clear();
		edgesCopies.clear();
		// topmost entities, the vertexes are guaranteed to be simple
		model.streamContained().filter(e -> e instanceof Vertex).map(e -> (Vertex) e).forEach(v -> {
			HashSet<Vertex> set = new HashSet<>();
			set.add(v.containedCopy());
			vertexCopies.put(v, set);
		});
		for(Definition d : defs) {
			d.streamContained().filter(e -> e instanceof Vertex).map(e -> (Vertex) e).forEach(v -> {
				// vertexes
				if (numCopies.get(d) > 1) {
					HashSet<Vertex> set = new HashSet<>();
					for(int i = 1; i <= numCopies.get(d); i++) {
						Vertex copy = v.containedCopy();
						copy.identifier += '/' + String.valueOf(i);
						set.add(copy);
					}
					vertexCopies.put(v, set);
				} else {
					HashSet<Vertex> set = new HashSet<>();
					set.add(v.containedCopy());
					vertexCopies.put(v, set);
				}
				// ports
				if (numCopies.get(d) > 1) {
					HashSet<Port> set = new HashSet<>();
					for(Port p : v.inPorts) {
						for(int i = 1; i <= numCopies.get(d); i++) {
							Port copy = p.containedCopy();
							copy.identifier += '/' + String.valueOf(i);
							set.add(copy);	
						}
						portsCopies.put(p, set);
					}
					for(Port p : v.outPorts) {
						for(int i = 1; i <= numCopies.get(d); i++) {
							Port copy = p.containedCopy();
							copy.identifier += '/' + String.valueOf(i);
							set.add(copy);	
						}
						portsCopies.put(p, set);
					}
				} else {
					for(Port p : v.inPorts) {
						HashSet<Port> set = new HashSet<>();
						set.add(p.containedCopy());
						portsCopies.put(p, set);
					}
					for(Port p : v.outPorts) {
						HashSet<Port> set = new HashSet<>();
						set.add(p.containedCopy());
						portsCopies.put(p, set);
					}
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
			d.streamContained().filter(e -> e instanceof Edge).map(e -> (Edge) e).forEach(e -> {
				// edges
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
		// now put everything together nesting wise
		for (Definition d : defs) {
			for (Definition copy : defsCopies.get(d)) {
				// TODO maybe later a general method can be made for adding vertexes arbitrarily, but for now 
				// this suffices.
				if (d instanceof CompoundProcess) {
					CompoundProcess dProc = (CompoundProcess) d;
					CompoundProcess copyProc = (CompoundProcess) copy;
					copyProc.netlist.vertexes.clear();
					copyProc.netlist.edges.clear();
					for (Vertex v : dProc.netlist.vertexes) {
						Vertex vCopy = vertexCopies.get(v).iterator().next();
						copyProc.netlist.vertexes.add(vCopy);
						vertexCopies.get(v).remove(vCopy);
					}
					for (Edge e : dProc.netlist.edges) {
						Edge eCopy = edgesCopies.get(e).iterator().next();
						copyProc.netlist.edges.add(eCopy);
						edgesCopies.get(e).remove(eCopy);
					}
				}
				// TODO the same must be done for platforms and bindings
				// finish by adding the definition
				flattenedModel.definitions.add(copy);
			}
		}
		//TODO missing to make the 
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
