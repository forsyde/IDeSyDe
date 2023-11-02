use std::{
    collections::{HashMap, HashSet},
    sync::Arc,
};

use idesyde_core::{DecisionModel, DesignModel, IdentificationResult};

use petgraph::{
    visit::{Bfs, GraphBase, IntoNeighbors, IntoNodeIdentifiers, Visitable},
    Direction::{Incoming, Outgoing},
    Graph, Undirected,
};

use crate::models::{
    AnalysedSDFApplication, AperiodicAsynchronousDataflow,
    AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore,
    AperiodicAsynchronousDataflowToPartitionedTiledMulticore, InstrumentedComputationTimes,
    InstrumentedMemoryRequirements, MemoryMappableMultiCore, PartitionedMemoryMappableMulticore,
    PartitionedTiledMulticore, RuntimesAndProcessors, TiledMultiCore,
};

pub fn identify_partitioned_mem_mapped_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut new_models = Vec::new();
    let mut errors: HashSet<String> = HashSet::new();
    for m2 in decision_models {
        if let Some(runt) = m2.downcast_ref::<RuntimesAndProcessors>() {
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
            if !one_proc_per_scheduler {
                errors.insert(
                    "identify_partitioned_mem_mapped_multicore: more than one processor per scheduler"
                        .to_string(),
                );
            }
            if !one_scheduler_per_proc {
                errors.insert(
                    "identify_partitioned_mem_mapped_multicore: more than one scheduler per processor"
                        .to_string(),
                );
            }
            if one_proc_per_scheduler && one_scheduler_per_proc {
                for m1 in decision_models {
                    if let Some(plat) = m1.downcast_ref::<MemoryMappableMultiCore>() {
                        let potential = Arc::new(PartitionedMemoryMappableMulticore {
                            hardware: plat.to_owned(),
                            runtimes: runt.to_owned(),
                        });
                        let upcast = potential as Arc<dyn DecisionModel>;
                        if !decision_models.contains(&upcast) {
                            new_models.push(upcast);
                        }
                    }
                }
            }
        }
    }
    (new_models, errors)
}

pub fn identify_partitioned_tiled_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut new_models = Vec::new();
    let mut errors: HashSet<String> = HashSet::new();
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
            if !same_number {
                errors.insert("identify_partitioned_tiled_multicore: number of schedulers and processores not equal".to_string());
            }
            if !one_proc_per_scheduler {
                errors.insert(
                    "identify_partitioned_tiled_multicore: more than one processor per scheduler"
                        .to_string(),
                );
            }
            if !one_scheduler_per_proc {
                errors.insert(
                    "identify_partitioned_tiled_multicore: more than one scheduler per processor"
                        .to_string(),
                );
            }
            if same_number && one_proc_per_scheduler && one_scheduler_per_proc {
                for m1 in decision_models {
                    if let Some(plat) = m1.downcast_ref::<TiledMultiCore>() {
                        let potential = Arc::new(PartitionedTiledMulticore {
                            hardware: plat.to_owned(),
                            runtimes: runt.to_owned(),
                        });
                        let upcast = potential as Arc<dyn DecisionModel>;
                        if !decision_models.contains(&upcast) {
                            new_models.push(upcast);
                        }
                    }
                }
            }
        }
    }
    (new_models, errors)
}

/// Identifies (many) AsynchronousAperiodicDataflow from AnalysedSDFApplication
///
/// The function is rather long and seems complex, but much of it is "plumbing"
/// for the analytical parts. Here is a breakdow of what this identification rule
/// is doing (you can find references within the code):
///
/// 1. Check if AnalysedSDFApplication(s) are identified,
/// 2. Find all weakly connected components (WCC) in AnalysedSDFApplication(s),
/// 3. build the job graph parameters for each WCC, for each AnalysedSDFApplication,
/// 4. return all the built AsynchronousAperiodicDataflow.
pub fn identify_asynchronous_aperiodic_dataflow_from_sdf(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut identified = Vec::new();
    let mut errors: HashSet<String> = HashSet::new();
    for m in decision_models {
        if let Some(analysed_sdf_application) = m.downcast_ref::<AnalysedSDFApplication>() {
            // build a temporary graph for analysis
            let mut total_actors_graph: Graph<&str, usize, petgraph::Directed> = Graph::new();
            let mut nodes = HashMap::new();
            for a in &analysed_sdf_application.sdf_application.actors_identifiers {
                nodes.insert(a, total_actors_graph.add_node(a.as_str()));
            }
            for (idx, (src, dst)) in analysed_sdf_application
                .sdf_application
                .topology_srcs
                .iter()
                .zip(
                    analysed_sdf_application
                        .sdf_application
                        .topology_dsts
                        .iter(),
                )
                .enumerate()
            {
                total_actors_graph.add_edge(
                    nodes.get(src).unwrap().to_owned(),
                    nodes.get(dst).unwrap().to_owned(),
                    idx,
                );
            }
            let undirected = total_actors_graph.clone().into_edge_type::<Undirected>();
            let wccs = weakly_connected_components(&undirected);
            // analysize each weakly connected component and build the entries for the dataflow model
            for wcc in wccs {
                // this first section build parameters of the WCC and
                // figure out the dataflow parts at the process level
                let component_actors: Vec<&str> = wcc
                    .iter()
                    .map(|x| {
                        *total_actors_graph.node_weight(*x).expect(
                            "Impossible lack of node wieight in analysed identification rule",
                        )
                    })
                    .collect();
                let component_channels: Vec<&str> = wcc
                    .iter()
                    .flat_map(|x| {
                        total_actors_graph
                            .edges_directed(*x, Outgoing)
                            .into_iter()
                            .flat_map(|e| {
                                analysed_sdf_application
                                    .sdf_application
                                    .topology_channel_names[*e.weight()]
                                .iter()
                            })
                    })
                    .map(|s| s.as_str())
                    .collect();
                let data_sent: HashMap<String, HashMap<String, u64>> = wcc
                    .iter()
                    .map(|x| {
                        (
                            total_actors_graph.node_weight(*x).unwrap().to_string(),
                            total_actors_graph
                                .edges_directed(*x, Outgoing)
                                .into_iter()
                                .flat_map(|e| {
                                    analysed_sdf_application
                                        .sdf_application
                                        .topology_channel_names[*e.weight()]
                                    .iter()
                                    .map(move |l| {
                                        (
                                            l.to_owned(),
                                            analysed_sdf_application
                                                .sdf_application
                                                .topology_production[*e.weight()]
                                                * analysed_sdf_application
                                                    .sdf_application
                                                    .topology_token_size_in_bits[*e.weight()],
                                        )
                                    })
                                })
                                .collect(),
                        )
                    })
                    .collect();
                let data_read: HashMap<String, HashMap<String, u64>> = wcc
                    .iter()
                    .map(|x| {
                        (
                            total_actors_graph.node_weight(*x).unwrap().to_string(),
                            total_actors_graph
                                .edges_directed(*x, Incoming)
                                .into_iter()
                                .flat_map(|e| {
                                    analysed_sdf_application
                                        .sdf_application
                                        .topology_channel_names[*e.weight()]
                                    .iter()
                                    .map(move |l| {
                                        (
                                            l.to_owned(),
                                            analysed_sdf_application
                                                .sdf_application
                                                .topology_consumption[*e.weight()]
                                                * analysed_sdf_application
                                                    .sdf_application
                                                    .topology_token_size_in_bits[*e.weight()],
                                        )
                                    })
                                })
                                .collect(),
                        )
                    })
                    .collect();
                // now we proceed to build the job graph in a mutable manner
                let jobs_of_processes: Vec<(&str, u64)> = component_actors
                    .iter()
                    .flat_map(|a| {
                        (1..=*analysed_sdf_application.repetition_vector.get(*a).unwrap())
                            .map(|q| (*a, q))
                    })
                    .collect();
                let mut job_graph_edges: Vec<((&str, u64), (&str, u64), bool)> = Vec::new();
                for (src, q_src) in &jobs_of_processes {
                    for (dst, q_dst) in &jobs_of_processes {
                        if let Some((cidx, _)) = analysed_sdf_application
                            .sdf_application
                            .topology_srcs
                            .iter()
                            .zip(
                                analysed_sdf_application
                                    .sdf_application
                                    .topology_dsts
                                    .iter(),
                            )
                            .enumerate()
                            .find(|(_, (s, t))| s == src && t == dst)
                        {
                            // let q_src_max = analysed_sdf_application.repetition_vector.get(*src).expect("Impossible empty entry for repetition vector during identification rule");
                            let consumed = analysed_sdf_application
                                .sdf_application
                                .topology_consumption[cidx];
                            let produced =
                                analysed_sdf_application.sdf_application.topology_production[cidx];
                            let initial_tokens = analysed_sdf_application
                                .sdf_application
                                .topology_initial_tokens[cidx];
                            let ratio =
                                ((q_dst * consumed - initial_tokens) as f64) / (produced as f64);
                            // if the jobs are different and the ratio of tokens is satisfied, they
                            // have a strong dependency, otherwise, they might only have a weak dependency
                            // or nothing at all.
                            if src != dst && *q_src == (ratio.ceil() as u64) {
                                job_graph_edges.push(((src, *q_src), (dst, *q_dst), true))
                            }
                        } else if src == dst && *q_dst == (*q_src + 1) {
                            job_graph_edges.push(((src, *q_src), (dst, *q_dst), false))
                        }
                    }
                }
                // we finish by building the decision model
                identified.push(Arc::new(AperiodicAsynchronousDataflow {
                    processes: component_actors.iter().map(|s| s.to_string()).collect(),
                    buffers: component_channels
                        .into_iter()
                        .map(|s| s.to_string())
                        .collect(),
                    buffer_max_size_in_bits: HashMap::new(),
                    job_graph_name: jobs_of_processes
                        .iter()
                        .map(|(s, _)| s.to_string())
                        .collect(),
                    job_graph_instance: jobs_of_processes.iter().map(|(_, q)| *q).collect(),
                    job_graph_src_name: job_graph_edges
                        .iter()
                        .map(|((s, _), _, _)| s.to_string())
                        .collect(),
                    job_graph_dst_name: job_graph_edges
                        .iter()
                        .map(|(_, (t, _), _)| t.to_string())
                        .collect(),
                    job_graph_src_instance: job_graph_edges
                        .iter()
                        .map(|((_, qs), _, _)| *qs)
                        .collect(),
                    job_graph_dst_instance: job_graph_edges
                        .iter()
                        .map(|(_, (_, qt), _)| *qt)
                        .collect(),
                    job_graph_is_strong_precedence: job_graph_edges
                        .iter()
                        .map(|(_, _, strong)| *strong)
                        .collect(),
                    process_minimum_throughput: analysed_sdf_application
                        .sdf_application
                        .actor_minimum_throughputs
                        .iter()
                        .filter(|(s, _)| component_actors.contains(&s.as_str()))
                        .map(|(s, f)| (s.to_owned(), *f))
                        .collect(),
                    process_path_maximum_latency: analysed_sdf_application
                        .sdf_application
                        .chain_maximum_latency
                        .iter()
                        .filter(|(s, _)| component_actors.contains(&s.as_str()))
                        .map(|(s, m)| (s.to_owned(), m.to_owned()))
                        .collect(),
                    process_put_in_buffer_in_bits: data_sent,
                    process_get_from_buffer_in_bits: data_read,
                }) as Arc<dyn DecisionModel>)
            }
            // Graph::from_edges(edges.into_iter());
            // actors_graph.extend_with_edges(edges.into_iter());
        } else {
            errors.insert("identify_asynchronous_aperiodic_dataflow_from_sdf: no AnalyzedSDFApplication detected".to_string());
        }
    }
    (identified, errors)
}

pub fn identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
    let mut errors: HashSet<String> = HashSet::new();
    if let Some(plat) = decision_models
        .iter()
        .find_map(|x| x.downcast_ref::<PartitionedTiledMulticore>())
    {
        if let Some(data) = decision_models
            .iter()
            .find_map(|x| x.downcast_ref::<InstrumentedComputationTimes>())
        {
            if let Some(mem_req) = decision_models
                .iter()
                .find_map(|x| x.downcast_ref::<InstrumentedMemoryRequirements>())
            {
                let apps: Vec<&AperiodicAsynchronousDataflow> = decision_models
                    .iter()
                    .flat_map(|x| x.downcast_ref::<AperiodicAsynchronousDataflow>())
                    .collect();
                // check if all processes can be mapped
                let first_non_mappable = apps
                    .iter()
                    .flat_map(|app| {
                        app.processes.iter().filter(|p| {
                            !plat.hardware.processors.iter().any(|pe| {
                                data.average_execution_times
                                    .get(*p)
                                    .map(|m| m.contains_key(pe))
                                    .unwrap_or(false)
                            })
                        })
                    })
                    .next();
                if apps.len() > 0 && first_non_mappable.is_some() {
                    errors.insert(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: process {} is not mappable in any processing element.", first_non_mappable.unwrap()));
                } else if apps.is_empty() {
                    errors.insert(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no asynchronous aperiodic application detected."));
                }
                if apps.len() > 0 && first_non_mappable.is_none() {
                    identified.push(Arc::new(
                        AperiodicAsynchronousDataflowToPartitionedTiledMulticore {
                            aperiodic_asynchronous_dataflows: apps
                                .into_iter()
                                .map(|x| x.to_owned())
                                .collect(),
                            partitioned_tiled_multicore: plat.to_owned(),
                            instrumented_computation_times: data.to_owned(),
                            instrumented_memory_requirements: mem_req.to_owned(),
                            processes_to_runtime_scheduling: HashMap::new(),
                            processes_to_memory_mapping: HashMap::new(),
                            buffer_to_memory_mappings: HashMap::new(),
                            super_loop_schedules: HashMap::new(),
                            processing_elements_to_routers_reservations: HashMap::new(),
                        },
                    ))
                }
            } else {
                errors.insert("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: no memory instrumentation decision model".to_string());
            }
        } else {
            errors.insert("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: no computational instrumentation decision model".to_string());
        }
    } else {
        errors.insert("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: no partitioned tiled platform model".to_string());
    }
    (identified, errors)
}

pub fn identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
    let mut errors: HashSet<String> = HashSet::new();
    if let Some(plat) = decision_models
        .iter()
        .find_map(|x| x.downcast_ref::<PartitionedMemoryMappableMulticore>())
    {
        if let Some(data) = decision_models
            .iter()
            .find_map(|x| x.downcast_ref::<InstrumentedComputationTimes>())
        {
            if let Some(mem_req) = decision_models
                .iter()
                .find_map(|x| x.downcast_ref::<InstrumentedMemoryRequirements>())
            {
                let apps: Vec<&AperiodicAsynchronousDataflow> = decision_models
                    .iter()
                    .flat_map(|x| x.downcast_ref::<AperiodicAsynchronousDataflow>())
                    .collect();
                // check if all processes can be mapped
                let first_non_mappable = apps
                    .iter()
                    .flat_map(|app| {
                        app.processes.iter().filter(|p| {
                            !plat.hardware.processing_elems.iter().any(|pe| {
                                data.average_execution_times
                                    .get(*p)
                                    .map(|m| m.contains_key(pe))
                                    .unwrap_or(false)
                            })
                        })
                    })
                    .next();
                if apps.len() > 0 && first_non_mappable.is_some() {
                    errors.insert(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: process {} is not mappable in any processing element.", first_non_mappable.unwrap()));
                } else if apps.is_empty() {
                    errors.insert(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no asynchronous aperiodic application detected."));
                }
                if apps.len() > 0 && first_non_mappable.is_none() {
                    identified.push(Arc::new(
                        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore {
                            aperiodic_asynchronous_dataflows: apps
                                .into_iter()
                                .map(|x| x.to_owned())
                                .collect(),
                            partitioned_mem_mappable_multicore: plat.to_owned(),
                            instrumented_computation_times: data.to_owned(),
                            instrumented_memory_requirements: mem_req.to_owned(),
                            processes_to_runtime_scheduling: HashMap::new(),
                            processes_to_memory_mapping: HashMap::new(),
                            buffer_to_memory_mappings: HashMap::new(),
                            super_loop_schedules: HashMap::new(),
                            processing_elements_to_routers_reservations: HashMap::new(),
                        },
                    ))
                }
            } else {
                errors.insert("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no memory instrumentation decision model".to_string());
            }
        } else {
            errors.insert("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no computational instrumentation decision model".to_string());
        }
    } else {
        errors.insert("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no mem mappable platform model".to_string());
    }
    (identified, errors)
}

/// Finds the weakly connected components (WCCs) of a directed graph
///
/// This auxiliary function exists because petgraph does not return
/// the actual WCC of a graph, but only counts them.
pub fn weakly_connected_components<G>(g: G) -> Vec<Vec<G::NodeId>>
where
    G: IntoNeighbors + Visitable + IntoNodeIdentifiers,
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
