package desyde.preprocessing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ForSyDe.Model.Core.Definition;
import ForSyDe.Model.Core.Edge;
import ForSyDe.Model.Core.Port;
import ForSyDe.Model.Core.Vertex;
import ForSyDe.Model.IO.ForSyDeIO;

public class Unroller {
	
	ForSyDeIO model;
	ForSyDeIO flattenedModel;
	
	public ForSyDeIO unrollModel(ForSyDeIO model) {
		if (this.model != model || flattenedModel == null) {
			flattenedModel = new ForSyDeIO();	
			unrollApps(model);
		}
		this.model = model;
		return flattenedModel;
	}
	
	protected void unrollApps(ForSyDeIO model) {
		Set<Vertex> vertexes = model.streamContained()
				.filter(v -> v instanceof Vertex)
				.map(v -> (Vertex) v)
				.collect(Collectors.toSet());
		Set<Edge> edges = model.streamContained()
				.filter(e -> e instanceof Edge)
				.map(e -> (Edge) e)
				.collect(Collectors.toSet());
		// initialize all elements as a single copy
		Map<Vertex, Integer> numCopies = vertexes.stream().collect(Collectors.toMap(v -> v, v -> 1));
		Map<Vertex, Set<Vertex>> scoped = new HashMap<>();
		for(Vertex v : vertexes) {
			Set<Vertex> children = v.definition.streamContained().filter(d -> d instanceof Vertex).map(e -> (Vertex) e)
					.collect(Collectors.toSet());
			scoped.put(v, children);
		}
//		Map<Vertex, Set<Vertex>> scoped = vertexes.stream()
//				.collect(Collectors.toMap(
//						// just get the vertex
//						v -> v,
//						// vertexes cascading
//						v -> v.definition.streamContained().filter(d -> d instanceof Vertex)
//						.peek(e -> System.out.println(e)).map(e -> (Vertex) e).collect(Collectors.toSet())
//						));
		// due to dynamic programming principles, you need to execute this V*(V-1) times.
		// TODO
		// the actual equation is: count the number of definitions to be cloned and use that to replicate the vertexes
		for (Vertex v1 : vertexes) {
			for (int i = 0; i < vertexes.size() - 1; i++) {
				// multiply all the incoming numbers
				Integer candidate = scoped.get(v1).stream().map(v -> numCopies.get(v)).reduce(1, (i1, i2) -> i1 * i2); 
				if (numCopies.get(v1) < candidate) {
					numCopies.put(v1, candidate);
				}
			}
		}
		// create all vertexes, ports and definition copies
		Map<Vertex, Set<Vertex>> vertexCopies = new HashMap<>();
		Map<Port, Set<Port>> portsCopies = new HashMap<>();
		Map<Definition, Set<Definition>> defsCopies = new HashMap<>();
		for(Vertex v : vertexes) {
			// vertexes
			if (numCopies.get(v) > 1) {
				Set<Vertex> set = new HashSet<>();
				for(int i = 1; i <= numCopies.get(v); i++) {
					Vertex copy = v.shallowCopy();
					copy.identifier += '/' + String.valueOf(i);
					set.add(copy);
				}
				vertexCopies.put(v, set);
			} else {
				Vertex copy = v.shallowCopy();
				vertexCopies.put(v, Set.of(copy));
			}
			// definitions
			if (numCopies.get(v) > 1) {
				Set<Definition> set = new HashSet<>();
				for(int i = 1; i <= numCopies.get(v); i++) {
					Definition copy = v.definition.shallowCopy();
					copy.identifier += '/' + String.valueOf(i);
					set.add(copy);
				}
				defsCopies.put(v.definition, set);
			} else {
				Definition copy = v.definition.shallowCopy();
				defsCopies.put(v.definition, Set.of(copy));
			}
			// ports
			if (numCopies.get(v) > 1) {
				Set<Port> set = new HashSet<>();
				for(Port p : v.inPorts) {
					for(int i = 1; i <= numCopies.get(v); i++) {
						Port copy = p.shallowCopy();
						copy.identifier += '/' + String.valueOf(i);
						set.add(copy);	
					}
					portsCopies.put(p, set);
				}
				for(Port p : v.outPorts) {
					for(int i = 1; i <= numCopies.get(v); i++) {
						Port copy = p.shallowCopy();
						copy.identifier += '/' + String.valueOf(i);
						set.add(copy);	
					}
					portsCopies.put(p, set);
				}
			} else {
				for(Port p : v.inPorts) {
					Port copy = p.shallowCopy();
					portsCopies.put(p, Set.of(copy));
				}
				for(Port p : v.outPorts) {
					Port copy = p.shallowCopy();
					portsCopies.put(p, Set.of(copy));
				}
			}
		}
		// create all edges and definitions, remember that the copies here are multiplications
		Map<Edge, Set<Edge>> edgesCopies = new HashMap<>();
		for(Edge e : edges) {
			Vertex to = e.toVertex;
			Vertex from = e.fromVertex;
			// edges
			if (numCopies.get(to)*numCopies.get(from) > 1) {
				Set<Edge> set = new HashSet<>();
				for(int i = 1; i <= numCopies.get(to)*numCopies.get(from); i++) {
					Edge copy = e.shallowCopy();
					copy.identifier += '/' + String.valueOf(i);
					set.add(copy);
				}
				edgesCopies.put(e, set);
			} else {
				Edge copy = e.shallowCopy();
				HashSet<Edge> set = new HashSet<>();
				set.add(copy);
				edgesCopies.put(e, Set.of(copy));
			}
			// definitions
			if (numCopies.get(to)*numCopies.get(from) > 1) {
				Set<Definition> set = new HashSet<>();
				for(int i = 1; i <= numCopies.get(to)*numCopies.get(from); i++) {
					Definition copy = e.definition.shallowCopy();
					copy.identifier += '/' + String.valueOf(i);
					set.add(copy);
				}
				if (defsCopies.containsKey(e.definition)) {
					defsCopies.get(e.definition).addAll(set);
				} else {
					defsCopies.put(e.definition, set);
				}
			} else {
				Definition copy = e.definition.shallowCopy();
				if (defsCopies.containsKey(e.definition)) {
					defsCopies.get(e.definition).add(copy);
				} else {
					HashSet<Definition> set = new HashSet<>();
					set.add(copy);
					defsCopies.put(e.definition, set);
				}
			}
		}
		
		// now put everything together
		
		System.out.println(":");
	}

}
