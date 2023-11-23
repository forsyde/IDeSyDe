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
    PartitionedTiledMulticore, RuntimesAndProcessors, SDFApplication, TiledMultiCore,
};

pub fn identify_partitioned_mem_mapped_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut new_models = Vec::new();
    let mut errors: Vec<String> = Vec::new();
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
                errors.push(
                    "identify_partitioned_mem_mapped_multicore: more than one processor per scheduler"
                        .to_string(),
                );
            }
            if !one_scheduler_per_proc {
                errors.push(
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
    let mut errors: Vec<String> = Vec::new();
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
                errors.push("identify_partitioned_tiled_multicore: number of schedulers and processores not equal".to_string());
            }
            if !one_proc_per_scheduler {
                errors.push(
                    "identify_partitioned_tiled_multicore: more than one processor per scheduler"
                        .to_string(),
                );
            }
            if !one_scheduler_per_proc {
                errors.push(
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
    let mut errors: Vec<String> = Vec::new();
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
                                                as u64
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
                                                as u64
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
                            let ratio = ((q_dst * consumed as u64 - initial_tokens as u64) as f64)
                                / (produced as f64);
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
                let channel_token_sizes = analysed_sdf_application
                    .sdf_application
                    .channel_token_sizes
                    .to_owned();
                // we finish by building the decision model
                identified.push(Arc::new(AperiodicAsynchronousDataflow {
                    processes: component_actors.iter().map(|s| s.to_string()).collect(),
                    buffers: component_channels
                        .into_iter()
                        .map(|s| s.to_string())
                        .collect(),
                    // this construciton takes a look in the topology and applies the maximum
                    // possible production of tokens to every channel lumped in such topology
                    buffer_max_size_in_bits: analysed_sdf_application
                        .sdf_application
                        .topology_channel_names
                        .iter()
                        .enumerate()
                        .flat_map(|(i, channels)| {
                            let src = &analysed_sdf_application.sdf_application.topology_srcs[i];
                            let prod =
                                analysed_sdf_application.sdf_application.topology_production[i];
                            let max_tokens = analysed_sdf_application
                                .repetition_vector
                                .get(src)
                                .map(|q_src| q_src * prod as u64)
                                .unwrap_or(prod as u64);
                            channels.iter().map(move |channel| (max_tokens, channel))
                        })
                        .map(|(max_tokens, channel)| {
                            (
                                channel.to_owned(),
                                channel_token_sizes
                                    .get(channel)
                                    .map(|tok| max_tokens * tok)
                                    .unwrap_or(0),
                            )
                        })
                        .collect(),
                    buffer_token_size_in_bits: channel_token_sizes,
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
            errors.push("identify_asynchronous_aperiodic_dataflow_from_sdf: no AnalyzedSDFApplication detected".to_string());
        }
    }
    (identified, errors)
}

pub fn identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
    let mut errors: Vec<String> = Vec::new();
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
                    errors.push(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: process {} is not mappable in any processing element.", first_non_mappable.unwrap()));
                } else if apps.is_empty() {
                    errors.push(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no asynchronous aperiodic application detected."));
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
                errors.push("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: no memory instrumentation decision model".to_string());
            }
        } else {
            errors.push("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: no computational instrumentation decision model".to_string());
        }
    } else {
        errors.push("identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore: no partitioned tiled platform model".to_string());
    }
    (identified, errors)
}

/// This identification rule enriches an SDFApplication with the repetition vector and a PASS.
pub fn identify_analyzed_sdf_from_common_sdf(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut identified = Vec::new();
    let mut msgs: Vec<String> = Vec::new();
    for m in decision_models {
        if let Some(sdf_application) = m.downcast_ref::<SDFApplication>() {
            // build up the matrix that captures the topology matrix
            let mut topology_matrix: Vec<Vec<i64>> = Vec::new();
            for (i, (src, dst)) in sdf_application
                .topology_srcs
                .iter()
                .zip(sdf_application.topology_dsts.iter())
                .enumerate()
            {
                // TODO: try to optimise this later
                let mut row = vec![0; sdf_application.actors_identifiers.len()];
                row[sdf_application
                    .actors_identifiers
                    .iter()
                    .position(|x| x == dst)
                    .unwrap()] = -(sdf_application.topology_consumption[i] as i64);
                row[sdf_application
                    .actors_identifiers
                    .iter()
                    .position(|x| x == src)
                    .unwrap()] = sdf_application.topology_production[i] as i64;
                topology_matrix.push(row);
            }
            let basis = compute_kernel_basis(&topology_matrix);
            let repetition_vector: HashMap<String, u64> = sdf_application
                .actors_identifiers
                .iter()
                .map(|a| {
                    (
                        a.to_owned(),
                        basis
                            .iter()
                            .map(|x| {
                                x[sdf_application
                                    .actors_identifiers
                                    .iter()
                                    .position(|y| y == a)
                                    .unwrap()]
                            })
                            .sum(),
                    )
                })
                .collect();
            if sdf_application
                .actors_identifiers
                .iter()
                .all(|a| repetition_vector.contains_key(a))
            {
                let actor_names: Vec<String> = sdf_application
                    .actors_identifiers
                    .iter()
                    .map(|a| a.to_owned())
                    .collect();
                let aggregated_repetition_vector: Vec<u64> = (0..actor_names.len())
                    .map(|i| basis.iter().map(|v| v[i]).sum())
                    .collect();
                let schedule = compute_periodic_admissible_static_schedule(
                    &topology_matrix,
                    &sdf_application.topology_initial_tokens,
                    &aggregated_repetition_vector,
                    &actor_names,
                );
                if schedule.is_empty() {
                    msgs.push("identify_analyzed_sdf_from_common_sdf: no periodic admissible static schedule found".to_string());
                } else {
                    identified.push(Arc::new(AnalysedSDFApplication {
                        sdf_application: sdf_application.to_owned(),
                        repetition_vector,
                        periodic_admissible_static_schedule: schedule,
                    }) as Arc<dyn DecisionModel>);
                }
            } else {
                msgs.push("identify_analyzed_sdf_from_common_sdf: repetition vector does not contain all actors".to_string());
            }
        }
    }
    IdentificationResult::from((identified, msgs))
}

pub fn identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore(
    _design_models: &Vec<Arc<dyn DesignModel>>,
    decision_models: &Vec<Arc<dyn DecisionModel>>,
) -> IdentificationResult {
    let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
    let mut errors: Vec<String> = Vec::new();
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
                    errors.push(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: process {} is not mappable in any processing element.", first_non_mappable.unwrap()));
                } else if apps.is_empty() {
                    errors.push(format!("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no asynchronous aperiodic application detected."));
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
                errors.push("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no memory instrumentation decision model".to_string());
            }
        } else {
            errors.push("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no computational instrumentation decision model".to_string());
        }
    } else {
        errors.push("identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore: no mem mappable platform model".to_string());
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

/// Computes the kernel vector base of a matrix exactly.
pub fn compute_kernel_basis(matrix: &Vec<Vec<i64>>) -> Vec<Vec<u64>> {
    let mut kernel_basis: Vec<Vec<u64>> = Vec::new();
    let num_rows = matrix.len();
    let num_cols = matrix[0].len();
    let identity_size = num_cols;
    let total_rows = num_rows + identity_size;

    // Create an identity matrix of the same size as the input matrix
    //  and augment the input matrix with the identity matrix
    let mut augmented_matrix: Vec<Vec<num::Rational64>> =
        vec![vec![num::zero(); num_cols]; num_rows + identity_size];
    for i in 0..num_rows {
        for j in 0..num_cols {
            augmented_matrix[i][j] = num::Rational64::from_integer(matrix[i][j] as i64);
        }
    }
    for i in num_rows..(num_rows + identity_size) {
        augmented_matrix[i][i - num_rows] = num::one();
    }

    // Perform row reduction using Gaussian elimination
    let mut pivot_row = 0;
    for col in 0..num_cols {
        // Find a non-zero pivot element in the current column
        let mut pivot_found = false;
        for row in pivot_row..num_rows {
            if augmented_matrix[row][col] != num::zero() {
                pivot_found = true;
                // Swap the pivot row with the current row
                if pivot_row != row {
                    // println!("swapping row {} with row {}", pivot_row, row);
                    augmented_matrix.swap(pivot_row, row);
                }
                break;
            }
        }

        // If no pivot element is found, move to the next column
        if !pivot_found {
            continue;
        }
        // println!("reducing a column with pivot {}", pivot_row);

        // normalize the current row
        for col_index in (col + 1)..num_cols {
            augmented_matrix[pivot_row][col_index] =
                augmented_matrix[pivot_row][col_index] / augmented_matrix[pivot_row][col];
        }
        // println!("pivoting row {}", pivot_row);
        augmented_matrix[pivot_row][col] = num::one();
        // Reduce the current column to have zeros below the pivot element
        for row in (pivot_row + 1)..total_rows {
            if augmented_matrix[row][col] != num::zero() {
                let pivot_multiple = augmented_matrix[row][col]; // / transpose_augmented[pivot_row][col];
                for col_index in col..num_cols {
                    augmented_matrix[row][col_index] = augmented_matrix[row][col_index]
                        - pivot_multiple * augmented_matrix[pivot_row][col_index];
                }
            }
        }

        // Move to the next pivot row
        pivot_row += 1;
    }
    // println!("[");
    // for row in 0..num_rows {
    //     for i in 0..num_cols {
    //         print!(
    //             "{}/{}, ",
    //             augmented_matrix[row][i].numer(),
    //             augmented_matrix[row][i].denom()
    //         );
    //     }
    //     println!()
    // }
    // println!("------");
    // for row in num_rows..(num_rows + identity_size) {
    //     for i in 0..num_cols {
    //         print!(
    //             "{}/{}, ",
    //             augmented_matrix[row][i].numer(),
    //             augmented_matrix[row][i].denom()
    //         );
    //     }
    //     println!()
    // }
    // println!("]");
    // permutate columns not in the kernel basis
    for pivot in 0..pivot_row {
        if augmented_matrix[pivot][pivot] != num::one() {
            let reduced_pos_opt = (0..num_cols).find(|i| augmented_matrix[pivot][*i] == num::one());
            if let Some(reduced_pos) = reduced_pos_opt {
                // println!("swapping columns {} and {}", reduced_pos, pivot);
                // swap the columns
                for i in 0..total_rows {
                    let temp = augmented_matrix[i][reduced_pos];
                    augmented_matrix[i][reduced_pos] = augmented_matrix[i][pivot];
                    augmented_matrix[i][pivot] = temp;
                }
            }
        }
    }

    // Extract the kernel vector base from the row-reduced augmented matrix
    // println!("actors: {}, pivot_row {}", num_cols, pivot_row);
    for col in pivot_row..num_cols {
        // println!("Building with column {}", col);
        let mut kernel_vector = vec![num::zero(); num_cols];
        for row in num_rows..(num_rows + identity_size) {
            kernel_vector[row - num_rows] = augmented_matrix[row][col];
        }
        // println!("kernel vector {:?}", kernel_vector);
        let dem_lcd = kernel_vector
            .iter()
            .map(|x| x.denom().to_owned())
            .filter(|x| x > &0)
            .reduce(num::integer::lcm)
            .unwrap_or(1);
        let num_gcd = kernel_vector
            .iter()
            .map(|x| x.numer().to_owned())
            .filter(|x| x > &0)
            .reduce(num::integer::gcd)
            .unwrap_or(1);
        let kernel_normalized = kernel_vector
            .iter()
            .map(|x| {
                x * num::Rational64::from_integer(dem_lcd) / num::Rational64::from_integer(num_gcd)
            })
            .map(|x| x.to_integer() as u64)
            .collect();
        // println!("kernel normalized {:?}", kernel_normalized);
        kernel_basis.push(kernel_normalized);
    }

    kernel_basis
}

pub fn compute_periodic_admissible_static_schedule(
    topology_matrix: &Vec<Vec<i64>>,
    initial_tokens: &Vec<u32>,
    repetition_vector: &Vec<u64>,
    actor_names: &Vec<String>,
) -> Vec<String> {
    let mut left = repetition_vector.clone();
    let mut buffer: Vec<u64> = initial_tokens.iter().map(|t| *t as u64).collect();
    let mut schedule = Vec::new();
    // println!("Initial buffer {:?}", buffer);
    // println!("compute schedule with left {:?}", left);
    while left.iter().any(|x| x > &0) {
        let mut can_schedule = false;
        for j in 0..actor_names.len() {
            if left[j] > 0 {
                let mut can_produce = true;
                for i in 0..initial_tokens.len() {
                    if topology_matrix[i][j] < 0 && buffer[i] < (-topology_matrix[i][j] as u64) {
                        can_produce = false;
                        break;
                    }
                }
                if can_produce {
                    for i in 0..initial_tokens.len() {
                        if topology_matrix[i][j] < 0 {
                            buffer[i] -= (-topology_matrix[i][j] as u64);
                        } else if topology_matrix[i][j] > 0 {
                            buffer[i] += topology_matrix[i][j] as u64;
                        }
                    }
                    left[j] -= 1;
                    can_schedule = true;
                    schedule.push(actor_names[j].to_owned());
                }
            }
        }
        if !can_schedule {
            // println!("Can t schedule");
            return vec![];
        }
    }
    // println!("done");
    schedule
}
