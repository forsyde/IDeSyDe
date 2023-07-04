use std::collections::HashMap;

use idesyde_core::{DecisionModel, DesignModel};
use petgraph::{
    algo::connected_components,
    visit::{
        Bfs, Dfs, GraphBase, IntoEdgeReferences, IntoNeighborsDirected, IntoNodeIdentifiers,
        NodeCompactIndexable, Visitable,
    },
    Graph,
};

use crate::models::{
    PartitionedTiledMulticore, RuntimesAndProcessors, SDFApplication, TiledMultiCore,
};

pub fn identify_partitioned_tiled_multicore(
    _design_models: &Vec<Box<dyn DesignModel>>,
    decision_models: &Vec<Box<dyn DecisionModel>>,
) -> Vec<Box<dyn DecisionModel>> {
    let mut new_models = Vec::new();
    for m2 in decision_models {
        if let Some(runt) = m2.downcast_ref::<RuntimesAndProcessors>() {
            let same_number = runt.processors.len() == runt.runtimes.len();
            let one_scheduler_per_proc = runt
                .processors
                .iter()
                .all(|p| runt.runtime_host.values().find(|y| y == &p).is_some());
            let one_proc_per_scheduler = runt.runtimes.iter().all(|s| {
                runt.processor_affinities
                    .values()
                    .find(|y| y == &s)
                    .is_some()
            });
            if same_number && one_proc_per_scheduler && one_scheduler_per_proc {
                for m1 in decision_models {
                    if let Some(plat) = m1.downcast_ref::<TiledMultiCore>() {
                        let potential = Box::new(PartitionedTiledMulticore {
                            hardware: plat.to_owned(),
                            runtimes: runt.to_owned(),
                        });
                        let upcast = potential as Box<dyn DecisionModel>;
                        if !decision_models.contains(&upcast) {
                            new_models.push(upcast);
                        }
                    }
                }
            }
        }
    }
    new_models
}

pub fn identify_asynchronous_aperiodic_dataflow_from_sdf(
    _design_models: &Vec<Box<dyn DesignModel>>,
    decision_models: &Vec<Box<dyn DecisionModel>>,
) -> Vec<Box<dyn DecisionModel>> {
    let mut identified = Vec::new();
    for m in decision_models {
        if let Some(sdf_application) = m.downcast_ref::<SDFApplication>() {
            let mut actors_graph: Graph<&str, (), petgraph::Directed> = Graph::new();
            let mut nodes = HashMap::new();
            for a in &sdf_application.actors_identifiers {
                nodes.insert(a, actors_graph.add_node(a.as_str()));
            }
            for (src, dst) in sdf_application
                .topology_srcs
                .iter()
                .zip(sdf_application.topology_dsts.iter())
            {
                actors_graph.add_edge(
                    nodes.get(src).unwrap().to_owned(),
                    nodes.get(dst).unwrap().to_owned(),
                    (),
                );
            }
            let wccs = weakly_connected_components(&actors_graph);
            for wcc in wccs {}
            // Graph::from_edges(edges.into_iter());
            // actors_graph.extend_with_edges(edges.into_iter());
        }
    }
    identified
}

pub fn weakly_connected_components<G>(g: G) -> Vec<Vec<G::NodeId>>
where
    G: IntoNeighborsDirected + Visitable + IntoNodeIdentifiers,
{
    let mut components = Vec::new();
    let mut visited: Vec<<G as GraphBase>::NodeId> = Vec::new();
    for node in g.node_identifiers() {
        if !visited.contains(&node) {
            let mut bfs = Bfs::new(g, node);
            let mut new_component = Vec::new();
            while let Some(v) = bfs.next(g) {
                new_component.push(v);
                visited.push(v);
            }
            components.push(new_component);
        }
    }
    components
}
