
use std::io::BufRead;
use std::process::Command;
use std::sync::{Arc, Mutex};
use std::vec;
use std::{
    collections::{HashMap, HashSet},
    io::BufReader,
};

use idesyde_common::models::{AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL, AperiodicAsynchronousDataflowToPartitionedTiledMulticore};
use idesyde_core::{ExplorationBid, ExplorationSolution, Explorer, RustEmbeddedModule};
use serde::Deserialize;

#[derive(Debug, Clone, PartialEq)]
#[allow(dead_code)]
enum MiniZincData {
    MznLitDouble(f64),
    MznLitInt(i32),
    MznLitLong(i64),
    MznLitString(String),
    MznLitBool(bool),
    MznOpt(Box<Option<MiniZincData>>),
    MznArray(Vec<MiniZincData>),
    MznSet(Vec<MiniZincData>),
    MznEnum(Vec<String>),
}

impl From<f64> for MiniZincData {
    fn from(value: f64) -> Self {
        MiniZincData::MznLitDouble(value)
    }
}

impl From<i32> for MiniZincData {
    fn from(value: i32) -> Self {
        MiniZincData::MznLitInt(value)
    }
}

impl From<u32> for MiniZincData {
    fn from(value: u32) -> Self {
        MiniZincData::MznLitInt(value as i32)
    }
}

impl From<i64> for MiniZincData {
    fn from(value: i64) -> Self {
        MiniZincData::MznLitLong(value)
    }
}

impl From<u64> for MiniZincData {
    fn from(value: u64) -> Self {
        MiniZincData::MznLitLong(value as i64)
    }
}

impl From<usize> for MiniZincData {
    fn from(value: usize) -> Self {
        MiniZincData::MznLitLong(value as i64)
    }
}

impl From<String> for MiniZincData {
    fn from(value: String) -> Self {
        MiniZincData::MznLitString(value)
    }
}

impl From<bool> for MiniZincData {
    fn from(value: bool) -> Self {
        MiniZincData::MznLitBool(value)
    }
}

impl<T: Into<MiniZincData>> From<Vec<T>> for MiniZincData {
    fn from(value: Vec<T>) -> Self {
        MiniZincData::MznArray(value.into_iter().map(|x| x.into()).collect())
    }
}

impl<T: Into<MiniZincData>> From<HashSet<T>> for MiniZincData {
    fn from(value: HashSet<T>) -> Self {
        MiniZincData::MznSet(value.into_iter().map(|x| x.into()).collect())
    }
}

impl<T: Into<MiniZincData>> From<Option<T>> for MiniZincData {
    fn from(value: Option<T>) -> Self {
        MiniZincData::MznOpt(Box::new(value.map(|x| x.into())))
    }
}

fn to_string(d: &MiniZincData) -> String {
    match d {
        MiniZincData::MznLitDouble(f) => f.to_string(),
        MiniZincData::MznLitInt(i) => i.to_string(),
        MiniZincData::MznLitLong(l) => l.to_string(),
        MiniZincData::MznLitString(s) => format!("\"{}\"", s),
        MiniZincData::MznLitBool(b) => b.to_string(),
        MiniZincData::MznArray(v) => {
            format!(
                "[{}]",
                v.into_iter()
                    .map(|x| to_string(x))
                    .collect::<Vec<String>>()
                    .join(",")
            )
        }
        MiniZincData::MznSet(v) => {
            format!(
                "{{\"set\": [{}]}}",
                v.into_iter()
                    .map(|x| to_string(x))
                    .collect::<Vec<String>>()
                    .join(",")
            )
        }
        MiniZincData::MznEnum(v) => {
            format!(
                "[{}]",
                v.into_iter()
                    .map(|x| format!("{{\"e\": {}}}", x))
                    .collect::<Vec<String>>()
                    .join(",")
            )
        }
        MiniZincData::MznOpt(o) => match o.as_ref() {
            Some(v) => to_string(v),
            None => "null".to_string(),
        },
    }
}

fn to_mzn_input(d: Vec<(&str, MiniZincData)>) -> String {
    format!(
        "{{{}}}",
        d.iter()
            .map(|(k, v)| format!("\"{}\": {}", k, to_string(v)))
            .collect::<Vec<String>>()
            .join(",")
    )
}

struct MiniZincGecodeExplorer;

impl Explorer for MiniZincGecodeExplorer {
    fn unique_identifier(&self) -> String {
        "MiniZincGecodeExplorer".to_string()
    }

    fn bid(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
    ) -> idesyde_core::ExplorationBid {
        if let Ok(_) = Command::new("minizinc").output() {
            if let Ok(aad2pmmmap) =
                AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL::try_from(
                    m.as_ref(),
                )
            {
                let mut objs = HashSet::new();
                objs.insert("nUsedPEs".to_string());
                for app in &aad2pmmmap.aperiodic_asynchronous_dataflows {
                    for p in app.processes.iter() {
                        objs.insert(format!("invThroughput({})", p));
                    }
                }
                return ExplorationBid {
                    can_explore: true,
                    is_exact: true,
                    competitiveness: 1.0,
                    target_objectives: objs,
                    additional_numeric_properties: HashMap::new(),
                };
            }
            if let Ok(aad2ptm) =
                AperiodicAsynchronousDataflowToPartitionedTiledMulticore::try_from(
                    m.as_ref(),
                )
            {
                let mut objs = HashSet::new();
                objs.insert("nUsedPEs".to_string());
                for app in &aad2ptm.aperiodic_asynchronous_dataflows {
                    for p in app.processes.iter() {
                        objs.insert(format!("invThroughput({})", p));
                    }
                }
                return ExplorationBid {
                    can_explore: true,
                    is_exact: true,
                    competitiveness: 1.0,
                    target_objectives: objs,
                    additional_numeric_properties: HashMap::new(),
                };
            }
        }
        ExplorationBid::impossible()
    }

    fn explore(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
        currrent_solutions: &std::collections::HashSet<idesyde_core::ExplorationSolution>,
        _exploration_configuration: idesyde_core::ExplorationConfiguration,
    ) -> Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>> {
        if let Ok(aad2pmmmap) =
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL::try_from(
                m.as_ref(),
            )
        {
            return solve_aad2pmmmap(&aad2pmmmap, currrent_solutions, "gecode");
        }
        if let Ok(aad2ptm) =
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore::try_from(
                m.as_ref(),
            )
        {
            return solve_aad2ptm(&aad2ptm, currrent_solutions, "gecode");
        }
        Arc::new(Mutex::new(std::iter::empty()))
    }
}

struct MiniZincORToolsExplorer;

impl Explorer for MiniZincORToolsExplorer {
    fn unique_identifier(&self) -> String {
        "MiniZincORToolsExplorer".to_string()
    }

    fn bid(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
    ) -> idesyde_core::ExplorationBid {
        if let Ok(_) = Command::new("minizinc").output() {
            if let Ok(aad2pmmmap) =
                AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL::try_from(
                    m.as_ref(),
                )
            {
                let mut objs = HashSet::new();
                objs.insert("nUsedPEs".to_string());
                for app in &aad2pmmmap.aperiodic_asynchronous_dataflows {
                    for p in app.processes.iter() {
                        objs.insert(format!("invThroughput({})", p));
                    }
                }
                return ExplorationBid {
                    can_explore: true,
                    is_exact: true,
                    competitiveness: 1.0,
                    target_objectives: objs,
                    additional_numeric_properties: HashMap::new(),
                };
            }
            if let Ok(aad2ptm) =
                AperiodicAsynchronousDataflowToPartitionedTiledMulticore::try_from(
                    m.as_ref(),
                )
            {
                let mut objs = HashSet::new();
                objs.insert("nUsedPEs".to_string());
                for app in &aad2ptm.aperiodic_asynchronous_dataflows {
                    for p in app.processes.iter() {
                        objs.insert(format!("invThroughput({})", p));
                    }
                }
                return ExplorationBid {
                    can_explore: true,
                    is_exact: true,
                    competitiveness: 1.0,
                    target_objectives: objs,
                    additional_numeric_properties: HashMap::new(),
                };
            }
        }
        ExplorationBid::impossible()
    }

    fn explore(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
        currrent_solutions: &std::collections::HashSet<idesyde_core::ExplorationSolution>,
        _exploration_configuration: idesyde_core::ExplorationConfiguration,
    ) -> Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>> {
        if let Ok(aad2pmmmap) =
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL::try_from(
                m.as_ref(),
            )
        {
            return solve_aad2pmmmap(&aad2pmmmap, currrent_solutions, "com.google.ortools.sat");
        }
        if let Ok(aad2ptm) =
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore::try_from(
                m.as_ref(),
            )
        {
            return solve_aad2ptm(&aad2ptm, currrent_solutions, "com.google.ortools.sat");
        }
        Arc::new(Mutex::new(std::iter::empty()))
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
struct MiniZincSolutionOutput<T> {
    #[serde(rename = "type")]
    kind: String,
    output: HashMap<String, T>,
    sections: Vec<String>,
}

const AADPMMMPL_MZN: &'static str =
    include_str!("AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL.mzn");

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
struct AADPMMMPLMznOutput {
    #[serde(rename = "processesExecution")]
    process_execution: Vec<u64>,
    #[serde(rename = "processesMapping")]
    process_mapping: Vec<u64>,
    #[serde(rename = "buffersMapping")]
    buffers_mapping: Vec<u64>,
    #[serde(rename = "communicationReservation")]
    communication_reservation: Vec<Vec<u64>>,
    #[serde(rename = "firingsOrdering")]
    firings_ordering: Vec<u64>,
    #[serde(rename = "invThroughput")]
    inv_throughput: Vec<u64>,
    #[serde(rename = "nUsedPEs")]
    n_used_pes: u64,
}

fn solve_aad2pmmmap(
    m: &AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL,
    current_solutions: &HashSet<ExplorationSolution>,
    explorer_name: &str,
) -> Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>> {
    let mut input_data = vec![];
    let all_processes: Vec<String> = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| app.processes.iter())
        .map(|x| x.to_string())
        .collect();
    let all_firings_actor: Vec<String> = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| app.job_graph_name.iter())
        .map(|x| x.to_string())
        .collect();
    let (all_buffers, all_buffer_max_sizes): (Vec<String>, Vec<u64>) = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| {
            app.buffers.iter().map(|b| {
                (
                    b.to_string(),
                    app.buffer_max_size_in_bits.get(b).map(|x| *x).unwrap_or(0),
                )
            })
        })
        .unzip();
    let memories: Vec<String> = m
        .partitioned_mem_mappable_multicore_and_pl
        .hardware
        .storage_elems
        .iter()
        .map(|x| x.to_string())
        .collect();
    let communications: Vec<String> = m
        .partitioned_mem_mappable_multicore_and_pl
        .hardware
        .communication_elems
        .iter()
        .map(|x| x.to_string())
        .collect();
    let list_schedulers: Vec<String> = m
        .partitioned_mem_mappable_multicore_and_pl
        .runtimes
        .runtimes
        .iter()
        .filter(|x| {
            m.partitioned_mem_mappable_multicore_and_pl
                .runtimes
                .is_super_loop
                .contains(*x)
        })
        .map(|x| x.to_string())
        .collect();
    let logic_areas: Vec<String> = m
        .partitioned_mem_mappable_multicore_and_pl
        .hardware
        .pl_module_available_areas
        .keys()
        .map(|x| x.to_string())
        .collect();
    let all_firings_instances: Vec<u64> = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| app.job_graph_instance.iter())
        .map(|i| i.to_owned())
        .collect();
    let all_firings: Vec<(&str, u64)> = all_firings_actor
        .iter()
        .map(|s| s.as_str())
        .zip(all_firings_instances.iter().map(|i| i.to_owned()))
        .collect();
    let mut connected: Vec<Vec<bool>> = all_processes
        .iter()
        .map(|_| vec![false; all_processes.len()])
        .collect();
    let mut firings_follows: Vec<HashSet<u64>> =
        all_firings.iter().map(|_| HashSet::new()).collect();
    let discrete_max = m.get_max_discrete_value() as f32;
    let average_max = m.get_max_average_execution_time();
    for app in &m.aperiodic_asynchronous_dataflows {
        for a in &app.processes {
            for aa in &app.processes {
                if a != aa {
                    if let Some(aidx) = all_processes.iter().position(|x| x == a) {
                        if let Some(aaidx) = all_processes.iter().position(|x| x == aa) {
                            connected[aidx][aaidx] = true;
                            connected[aaidx][aidx] = true;
                        }
                    }
                }
            }
        }
        let app_follows = app.job_follows();
        for (f, f_follows) in app_follows {
            if let Some(fidx) = all_firings.iter().position(|ff| f == *ff) {
                firings_follows[fidx].extend(
                    f_follows
                        .iter()
                        .map(|tgt| all_firings.iter().position(|ff| ff == tgt).unwrap() as u64),
                );
            }
        }
    }
    let execution_times: Vec<Vec<i32>> = all_processes
        .iter()
        .map(|f| {
            m.partitioned_mem_mappable_multicore_and_pl
                .hardware
                .processing_elems
                .iter()
                .filter(|pe| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .runtimes
                        .processor_affinities
                        .get(*pe)
                        .map(|r| {
                            m.partitioned_mem_mappable_multicore_and_pl
                                .runtimes
                                .is_super_loop
                                .contains(r)
                        })
                        .unwrap_or(false)
                })
                .map(|pe| {
                    m.instrumented_computation_times
                        .average_execution_times
                        .get(f)
                        .and_then(|inner| inner.get(pe))
                        .map(|x| {
                            ((*x as f32) / (m.instrumented_computation_times.scale_factor as f32)
                                * discrete_max
                                / average_max)
                                .ceil() as i32
                        })
                        .unwrap_or(-1)
                })
                .chain(
                    m.partitioned_mem_mappable_multicore_and_pl
                        .hardware
                        .programmable_logic_elems
                        .iter()
                        .map(|pla| {
                            if m.hardware_implementation_area
                                .required_resources
                                .get(f)
                                .and_then(|x| x.get(pla))
                                .is_some()
                            {
                                m.hardware_implementation_area
                                    .latencies_numerators
                                    .get(f)
                                    .and_then(|inner| inner.get(pla))
                                    .map(|x| {
                                        ((*x as f32)
                                            / *m.hardware_implementation_area
                                                .latencies_denominators
                                                .get(f)
                                                .and_then(|x| x.get(pla))
                                                .unwrap_or(&1)
                                                as f32
                                            * discrete_max
                                            / average_max)
                                            .ceil() as i32
                                    })
                                    .unwrap_or(-1)
                            } else {
                                -1
                            }
                        }),
                )
                .collect()
        })
        .collect();
    let mappable_to_comm: Vec<Vec<Option<HashSet<usize>>>> = list_schedulers
        .iter()
        .flat_map(|s| {
            m.partitioned_mem_mappable_multicore_and_pl
                .runtimes
                .runtime_host
                .get(s)
        })
        .chain(logic_areas.iter())
        .map(|pe| {
            memories
                .iter()
                .map(|me| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .hardware
                        .pre_computed_paths
                        .get(pe)
                        .and_then(|xs| xs.get(me))
                        .map(|xs| {
                            xs.iter()
                                .flat_map(|x| communications.iter().position(|ce| ce == x))
                                .collect()
                        })
                })
                .collect()
        })
        .collect();
    let comm_to_mappable: Vec<Vec<Option<HashSet<usize>>>> = memories
        .iter()
        .map(|me| {
            list_schedulers
                .iter()
                .flat_map(|s| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .runtimes
                        .runtime_host
                        .get(s)
                })
                .chain(logic_areas.iter())
                .map(|pe| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .hardware
                        .pre_computed_paths
                        .get(pe)
                        .and_then(|xs| xs.get(me))
                        .map(|xs| {
                            xs.iter()
                                .flat_map(|x| communications.iter().position(|ce| ce == x))
                                .collect()
                        })
                })
                .collect()
        })
        .collect();
    let memory_scale = m.get_memory_scale_factor();
    let processes_mem_size: Vec<Vec<u64>> = all_processes
        .iter()
        .map(|p| {
            list_schedulers
                .iter()
                .flat_map(|s| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .runtimes
                        .runtime_host
                        .get(s)
                })
                .chain(logic_areas.iter())
                .map(|pe| {
                    m.instrumented_memory_requirements
                        .memory_requirements
                        .get(p)
                        .and_then(|inner| inner.get(pe))
                        .map(|x| *x / memory_scale)
                        .unwrap_or(0)
                })
                .collect()
        })
        .collect();
    let programmable_scale_factors = m.get_requirements_scale_factors();
    let programmable_resources_set: Vec<String> = programmable_scale_factors
        .keys()
        .map(String::to_string)
        .collect();
    let processes_area_requirements: Vec<Vec<Vec<u64>>> = all_processes
        .iter()
        .map(|p| {
            logic_areas
                .iter()
                .map(|pla| {
                    programmable_resources_set
                        .iter()
                        .map(|req| {
                            m.hardware_implementation_area
                                .required_resources
                                .get(p)
                                .and_then(|inner| inner.get(pla))
                                .and_then(|x| x.get(req))
                                .map(|x| *x / programmable_scale_factors.get(req).unwrap_or(&1))
                                .unwrap_or(0)
                        })
                        .collect()
                })
                .collect()
        })
        .collect();
    input_data.push((
        "Processes",
        MiniZincData::from(
            (0..all_processes.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Buffers",
        MiniZincData::from(
            (0..all_buffers.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Firings",
        MiniZincData::from(
            (0..all_firings_actor.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Memories",
        MiniZincData::from((0..memories.len()).map(|x| x as i32).collect::<Vec<i32>>()),
    ));
    input_data.push((
        "Communications",
        MiniZincData::from(
            (0..communications.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "ListSchedulers",
        MiniZincData::from(
            (0..list_schedulers.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "LogicAreas",
        MiniZincData::from(
            (0..logic_areas.len())
                .map(|x| (list_schedulers.len() + x) as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "LogicAreaRequirements",
        MiniZincData::from(
            (0..programmable_resources_set.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "firingsActor",
        MiniZincData::from(
            all_firings_actor
                .iter()
                .map(|f| {
                    all_processes
                        .iter()
                        .position(|a| a == f)
                        .expect("Firings could not find actors with its name")
                })
                .map(|x| x as u64)
                .collect::<Vec<u64>>(),
        ),
    ));
    input_data.push((
        "firingsNumber",
        MiniZincData::from(all_firings_instances.clone()),
    ));
    input_data.push(("follows", MiniZincData::from(firings_follows)));
    input_data.push((
        "slots",
        MiniZincData::from(
            communications
                .iter()
                .map(|c| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .hardware
                        .communication_elements_max_channels
                        .get(c)
                })
                .map(|x| x.map(|y| *y as u64).unwrap_or(0))
                .collect::<Vec<u64>>(),
        ),
    ));
    input_data.push((
        "memorySize",
        MiniZincData::from(
            memories
                .iter()
                .map(|me| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .hardware
                        .storage_sizes
                        .get(me)
                        .map(|x| (*x / memory_scale) as i32)
                        .unwrap_or(0)
                })
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "logicAreaCapacity",
        MiniZincData::from(
            logic_areas
                .iter()
                .map(|pla| {
                    programmable_resources_set
                        .iter()
                        .map(|req| {
                            m.hardware_implementation_area
                                .provided_resources
                                .get(pla)
                                .and_then(|x| {
                                    x.get(req).map(|y| {
                                        (*y as u64)
                                            / programmable_scale_factors.get(req).unwrap_or(&1)
                                    })
                                })
                                .unwrap_or(0)
                        })
                        .collect::<Vec<u64>>()
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));
    input_data.push(("bufferSize", MiniZincData::from(all_buffer_max_sizes)));
    input_data.push(("processesMemSize", MiniZincData::from(processes_mem_size)));
    input_data.push((
        "processesAreaRequirements",
        MiniZincData::from(processes_area_requirements),
    ));
    input_data.push((
        "processesReadBuffer",
        MiniZincData::from(
            all_processes
                .iter()
                .map(|p| {
                    all_buffers
                        .iter()
                        .map(|b| {
                            m.aperiodic_asynchronous_dataflows
                                .iter()
                                .flat_map(|app| {
                                    app.process_get_from_buffer_in_bits
                                        .get(p)
                                        .and_then(|x| x.get(b).map(|y| *y / memory_scale))
                                })
                                .sum::<u64>()
                        })
                        .collect::<Vec<u64>>()
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));
    input_data.push((
        "processesWriteBuffer",
        MiniZincData::from(
            all_processes
                .iter()
                .map(|p| {
                    all_buffers
                        .iter()
                        .map(|b| {
                            m.aperiodic_asynchronous_dataflows
                                .iter()
                                .flat_map(|app| {
                                    app.process_put_in_buffer_in_bits
                                        .get(p)
                                        .and_then(|x| x.get(b).map(|y| *y / memory_scale))
                                })
                                .sum::<u64>()
                        })
                        .collect::<Vec<u64>>()
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));

    input_data.push((
        "interconnectToMemories",
        MiniZincData::from(mappable_to_comm),
    ));
    input_data.push((
        "interconnectFromMemories",
        MiniZincData::from(comm_to_mappable),
    ));
    input_data.push(("executionTime", MiniZincData::from(execution_times)));
    input_data.push((
        "invBandwidthPerChannel",
        MiniZincData::from(
            communications
                .iter()
                .map(|ce| {
                    m.partitioned_mem_mappable_multicore_and_pl
                        .hardware
                        .communication_elements_bit_per_sec_per_channel
                        .get(ce)
                        .map(|x| {
                            (average_max as f64 / x / discrete_max as f64 / memory_scale as f64)
                                .ceil() as i32
                        })
                        .unwrap_or(0)
                })
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "nPareto",
        MiniZincData::from(current_solutions.len() as u64),
    ));
    input_data.push((
        "previousSolutions",
        MiniZincData::from(
            current_solutions
                .iter()
                .map(|s| {
                    let mut objs = vec![];
                    objs.push((*s.objectives.get("nUsedPEs").unwrap_or(&0.0)) as u64);
                    for p in &all_processes {
                        objs.push(
                            *s.objectives
                                .get(&format!("invThroughput({})", p))
                                .unwrap_or(&0.0) as u64,
                        );
                    }
                    objs
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));
    input_data.push(("connected", MiniZincData::from(connected)));

    let temp_dir = std::env::temp_dir().join("idesyde").join("minizinc");
    let model_file = temp_dir.join("AADPMMMPL.mzn");
    let data_file = temp_dir.join("AADPMMMPL.json");
    std::fs::create_dir_all(&temp_dir).expect("Could not create the temporary directory");
    std::fs::write(&model_file, AADPMMMPL_MZN).expect("Could not write the model file");
    std::fs::write(&data_file, to_mzn_input(input_data)).expect("Could not write the data file");
    if let Ok(proc) = std::process::Command::new("minizinc")
        .arg("-f")
        .arg("--solver")
        .arg(explorer_name)
        .arg("--json-stream")
        .arg("--output-mode")
        .arg("json")
        .arg(data_file.as_path())
        .arg(model_file.as_path())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::null())
        .spawn()
    {
        if let Some(stdout) = proc.stdout {
            let bufreader = BufReader::new(stdout);
            let input = m.clone();
            return Arc::new(Mutex::new(
                bufreader
                    .lines()
                    .take_while(|l| l.is_ok())
                    // .inspect(|l| {
                    //     if let Ok(line) = l {
                    //         println!("{}", line);
                    //     }
                    // })
                    .flat_map(move |line_r| {
                        if let Ok(line) = line_r {
                            if line.contains("UNSATISFIABLE") {
                                return None;
                            } else if line.contains("output") {
                                let mzn_out: MiniZincSolutionOutput<AADPMMMPLMznOutput> =
                                    serde_json::from_str(line.as_str())
                                        .expect("Should not fail to parse the output of minizinc");
                                let mut explored = input.clone();
                                if let Some(mzn_vars) = mzn_out.output.get("json") {
                                    let list_schedulers_mapping: HashMap<String, String> = mzn_vars
                                        .process_execution
                                        .iter()
                                        .enumerate()
                                        .filter(|(_, r)| **r < list_schedulers.len() as u64)
                                        .map(|(p, r)| {
                                            (
                                                all_processes[p].clone(),
                                                list_schedulers[*r as usize].clone(),
                                            )
                                        })
                                        .collect();
                                    explored.processes_to_runtime_scheduling =
                                        list_schedulers_mapping.clone();
                                    explored.processes_to_logic_programmable_areas = mzn_vars
                                        .process_execution
                                        .iter()
                                        .enumerate()
                                        .filter(|(_, r)| **r >= list_schedulers.len() as u64)
                                        .map(|(p, r)| {
                                            (
                                                all_processes[p].clone(),
                                                logic_areas[(*r as usize) - list_schedulers.len()]
                                                    .clone(),
                                            )
                                        })
                                        .collect();
                                    explored.processes_to_memory_mapping = mzn_vars
                                        .process_mapping
                                        .iter()
                                        .enumerate()
                                        .map(|(p, r)| {
                                            (
                                                all_processes[p].clone(),
                                                memories[*r as usize].clone(),
                                            )
                                        })
                                        .collect();
                                    explored.buffer_to_memory_mappings = mzn_vars
                                        .buffers_mapping
                                        .iter()
                                        .enumerate()
                                        .map(|(b, m)| {
                                            (all_buffers[b].clone(), memories[*m as usize].clone())
                                        })
                                        .collect();
                                    let firings: Vec<(&str, u64)> = all_firings_actor
                                        .iter()
                                        .map(|s| s.as_str())
                                        .zip(all_firings_instances.iter().map(|i| i.to_owned()))
                                        .collect();
                                    explored.super_loop_schedules = list_schedulers
                                        .iter()
                                        .map(|pe| {
                                            let mut looplist: Vec<(String, u64)> = firings
                                                .iter()
                                                .zip(mzn_vars.firings_ordering.iter())
                                                .filter(|((a, _), _)| {
                                                    list_schedulers_mapping
                                                        .get(*a)
                                                        .map(|x| pe == x)
                                                        .unwrap_or(false)
                                                })
                                                .map(|((a, _), idx)| (a.to_string(), *idx))
                                                .collect();
                                            looplist.sort_by_key(|(_, idx)| *idx);
                                            (
                                                pe.clone(),
                                                looplist.into_iter().map(|(a, _)| a).collect(),
                                            )
                                        })
                                        .collect();
                                    explored.processing_elements_to_routers_reservations =
                                        list_schedulers
                                            .iter()
                                            .chain(logic_areas.iter())
                                            .zip(mzn_vars.communication_reservation.iter())
                                            .map(|(pe, res)| {
                                                (
                                                    pe.clone(),
                                                    communications
                                                        .iter()
                                                        .zip(res.iter())
                                                        .filter(|(_, r)| **r > 0)
                                                        .map(|(c, r)| (c.clone(), *r as u16))
                                                        .collect(),
                                                )
                                            })
                                            .collect();
                                    let mut objs = HashMap::new();
                                    objs.insert("nUsedPEs".to_string(), mzn_vars.n_used_pes as f64);
                                    for (p, inv) in mzn_vars.inv_throughput.iter().enumerate() {
                                        objs.insert(
                                            format!("invThroughput({})", all_processes[p]),
                                            *inv as f64,
                                        );
                                    }
                                    return Some(ExplorationSolution {
                                        solved: Arc::new(explored),
                                        objectives: objs,
                                    });
                                }
                            }
                        }
                        None
                    }),
            ));
        }
    };
    Arc::new(Mutex::new(std::iter::empty::<ExplorationSolution>()))
}

const AADPTM_MZN: &'static str =
    include_str!("AperiodicAsynchronousDataflowToPartitionedTiledMulticore.mzn");

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
struct AADPTMMznOutput {
    #[serde(rename = "processMapping")]
    process_mapping: Vec<u64>,
    #[serde(rename = "buffersMapping")]
    buffers_mapping: Vec<u64>,
    #[serde(rename = "communicationReservation")]
    communication_reservation: Vec<Vec<u64>>,
    #[serde(rename = "firingsOrdering")]
    firings_ordering: Vec<u64>,
    #[serde(rename = "invThroughput")]
    inv_throughput: Vec<u64>,
    #[serde(rename = "nUsedPEs")]
    n_used_pes: u64,
}

fn solve_aad2ptm(
    m: &AperiodicAsynchronousDataflowToPartitionedTiledMulticore,
    current_solutions: &HashSet<ExplorationSolution>,
    explorer_name: &str,
) -> Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>> {
    let mut input_data = vec![];
    let mut messages: Vec<String> = vec![];
    let mut messages_sender: Vec<usize> = vec![];
    let mut messages_receiver: Vec<usize> = vec![];
    let all_firings_actor: Vec<String> = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| app.job_graph_name.iter())
        .map(|x| x.to_string())
        .collect();
    let all_firings_instances: Vec<u64> = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| app.job_graph_instance.iter())
        .map(|i| i.to_owned())
        .collect();
    let all_firings: Vec<(&str, u64)> = all_firings_actor
        .iter()
        .map(|s| s.as_str())
        .zip(all_firings_instances.iter().map(|i| i.to_owned()))
        .collect();
    for app in &m.aperiodic_asynchronous_dataflows {
        for (((srca, srcq), dsta), dstq) in app.job_graph_src_name.iter().zip(app.job_graph_src_instance.iter()).zip(app.job_graph_dst_name.iter()).zip(app.job_graph_dst_instance.iter()) {
            let firing_src_idx = all_firings.iter().position(|(f, i)| f == srca && i == srcq).unwrap();
            let firings_dst_idx = all_firings.iter().position(|(f, i)| f == dsta && i == dstq).unwrap();
            for c in &app.buffers {
                if app.process_put_in_buffer_in_bits.get(srca).and_then(|x| x.get(c)).unwrap_or(&0) > &0
                    && app.process_get_from_buffer_in_bits.get(dsta).and_then(|x| x.get(c)).unwrap_or(&0) > &0
                {
                    messages.push(c.clone());
                    messages_sender.push(firing_src_idx);
                    messages_receiver.push(firings_dst_idx);
                }
            }
        }
    }
    let all_processes: Vec<String> = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| app.processes.iter())
        .map(|x| x.to_string())
        .collect();
    let (all_buffers, all_buffer_max_sizes): (Vec<String>, Vec<u64>) = m
        .aperiodic_asynchronous_dataflows
        .iter()
        .flat_map(|app| {
            app.buffers.iter().map(|b| {
                (
                    b.to_string(),
                    app.buffer_max_size_in_bits.get(b).map(|x| *x).unwrap_or(0),
                )
            })
        })
        .unzip();
    let communications: Vec<String> = m
        .partitioned_tiled_multicore
        .hardware
        .routers
        .iter()
        .chain(m
            .partitioned_tiled_multicore
            .hardware
            .network_interfaces.iter())
        .map(|x| x.to_string())
        .collect();
    let list_schedulers: Vec<String> = m
        .partitioned_tiled_multicore
        .runtimes
        .runtimes
        .iter()
        .filter(|x| {
            m.partitioned_tiled_multicore
                .runtimes
                .is_super_loop
                .contains(*x)
        })
        .map(|x| x.to_string())
        .collect();
    let mut connected: Vec<Vec<bool>> = all_processes
        .iter()
        .map(|_| vec![false; all_processes.len()])
        .collect();
    let mut firings_follows: Vec<HashSet<u64>> =
        all_firings.iter().map(|_| HashSet::new()).collect();
    let memories = m.partitioned_tiled_multicore.hardware.memories.clone();
    let discrete_max = m.get_max_discrete_value() as f32;
    let average_max = m.get_max_average_execution_time();
    for app in &m.aperiodic_asynchronous_dataflows {
        for a in &app.processes {
            for aa in &app.processes {
                if a != aa {
                    if let Some(aidx) = all_processes.iter().position(|x| x == a) {
                        if let Some(aaidx) = all_processes.iter().position(|x| x == aa) {
                            connected[aidx][aaidx] = true;
                            connected[aaidx][aidx] = true;
                        }
                    }
                }
            }
        }
        let app_follows = app.job_follows();
        for (f, f_follows) in app_follows {
            if let Some(fidx) = all_firings.iter().position(|ff| f == *ff) {
                firings_follows[fidx].extend(
                    f_follows
                        .iter()
                        .map(|tgt| all_firings.iter().position(|ff| ff == tgt).unwrap() as u64),
                );
            }
        }
    }
    let execution_times: Vec<Vec<i32>> = all_processes
        .iter()
        .map(|f| {
            list_schedulers
                .iter()
                .flat_map(|r| {
                    m.partitioned_tiled_multicore
                        .runtimes
                        .runtime_host
                        .get(r)
                })
                .map(|pe| {
                    m.instrumented_computation_times
                        .average_execution_times
                        .get(f)
                        .and_then(|inner| inner.get(pe))
                        .map(|x| {
                            ((*x as f32) / (m.instrumented_computation_times.scale_factor as f32)
                                * discrete_max
                                / average_max)
                                .ceil() as i32
                        })
                        .unwrap_or(-1)
                })
                .collect()
        })
        .collect();
    let mappable_to_mappable: Vec<Vec<Option<HashSet<usize>>>> = list_schedulers
        .iter()
        .flat_map(|s: &String| {
            m.partitioned_tiled_multicore
                .runtimes
                .runtime_host
                .get(s)
        })
        .map(|src| {
            list_schedulers
                .iter()
                .flat_map(|t: &String| 
                    m.partitioned_tiled_multicore
                        .runtimes
                        .runtime_host
                        .get(t))
                .map(|tgt| {
                    m.partitioned_tiled_multicore
                        .hardware
                        .pre_computed_paths
                        .get(src)
                        .and_then(|xs| xs.get(tgt))
                        .map(|xs| {
                            xs.iter()
                                .flat_map(|x| communications.iter().position(|ce| ce == x))
                                .collect()
                        })
                })
                .collect()
        })
        .collect();
    let memory_scale = m.get_memory_scale_factor();
    let processes_mem_size: Vec<Vec<u64>> = all_processes
        .iter()
        .map(|p| {
            list_schedulers
                .iter()
                .flat_map(|s| {
                    m.partitioned_tiled_multicore
                        .runtimes
                        .runtime_host
                        .get(s)
                })
                .map(|pe| {
                    m.instrumented_memory_requirements
                        .memory_requirements
                        .get(p)
                        .and_then(|inner| inner.get(pe))
                        .map(|x| *x / memory_scale)
                        .unwrap_or(0)
                })
                .collect()
        })
        .collect();
    input_data.push((
        "Processes",
        MiniZincData::from(
            (0..all_processes.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Buffers",
        MiniZincData::from(
            (0..all_buffers.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Firings",
        MiniZincData::from(
            (0..all_firings_actor.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Messages",
        MiniZincData::from(
            (0..messages.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Communications",
        MiniZincData::from(
            (0..communications.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "Tiles",
        MiniZincData::from(
            (0..list_schedulers.len())
                .map(|x| x as i32)
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "firingsActor",
        MiniZincData::from(
            all_firings_actor
                .iter()
                .map(|f| {
                    all_processes
                        .iter()
                        .position(|a| a == f)
                        .expect("Firings could not find actors with its name")
                })
                .map(|x| x as u64)
                .collect::<Vec<u64>>(),
        ),
    ));
    input_data.push((
        "firingsNumber",
        MiniZincData::from(all_firings_instances.clone()),
    ));
    input_data.push(("follows", MiniZincData::from(firings_follows)));
    input_data.push(("receiver", MiniZincData::from(messages_receiver)));
    input_data.push(("sender", MiniZincData::from(messages_sender)));
    input_data.push(("messageBuffer", MiniZincData::from(messages.iter().map(|x| all_buffers.iter().position(|y| y == x).unwrap() as u64).collect::<Vec<u64>>())));
    input_data.push((
        "slots",
        MiniZincData::from(
            communications
                .iter()
                .map(|c| {
                    m.partitioned_tiled_multicore
                        .hardware
                        .communication_elements_max_channels
                        .get(c)
                })
                .map(|x| x.map(|y| *y as u64).unwrap_or(0))
                .collect::<Vec<u64>>(),
        ),
    ));
    input_data.push((
        "frameSize",
        MiniZincData::from(
            communications
                .iter()
                .map(|_| {
                    0
                })
                // .map(|x| x.map(|y| *y as u64).unwrap_or(0))
                .collect::<Vec<u64>>(),
        ),
    ));
    input_data.push((
        "memorySize",
        MiniZincData::from(
            m.partitioned_tiled_multicore.hardware.processors
                .iter()
                .map(|pe| {
                    m.partitioned_tiled_multicore
                        .hardware
                        .tile_memory_sizes
                        .get(pe)
                        .map(|x| (*x / memory_scale) as i32)
                        .unwrap_or(0)
                })
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push(("bufferSize", MiniZincData::from(all_buffer_max_sizes)));
    input_data.push(("processesMemSize", MiniZincData::from(processes_mem_size)));
    input_data.push((
        "processesReadBuffer",
        MiniZincData::from(
            all_processes
                .iter()
                .map(|p| {
                    all_buffers
                        .iter()
                        .map(|b| {
                            m.aperiodic_asynchronous_dataflows
                                .iter()
                                .flat_map(|app| {
                                    app.process_get_from_buffer_in_bits
                                        .get(p)
                                        .and_then(|x| x.get(b).map(|y| *y / memory_scale))
                                })
                                .sum::<u64>()
                        })
                        .collect::<Vec<u64>>()
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));
    input_data.push((
        "processesWriteBuffer",
        MiniZincData::from(
            all_processes
                .iter()
                .map(|p| {
                    all_buffers
                        .iter()
                        .map(|b| {
                            m.aperiodic_asynchronous_dataflows
                                .iter()
                                .flat_map(|app| {
                                    app.process_put_in_buffer_in_bits
                                        .get(p)
                                        .and_then(|x| x.get(b).map(|y| *y / memory_scale))
                                })
                                .sum::<u64>()
                        })
                        .collect::<Vec<u64>>()
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));

    input_data.push((
        "hasInterconnectTo",
        MiniZincData::from(mappable_to_mappable.iter().map(|x| x.iter().map(|y| y.is_some()).collect()).collect::<Vec<Vec<bool>>>()),
    ));
    input_data.push((
        "interconnectTo",
        MiniZincData::from(mappable_to_mappable.iter().map(|x| x.iter().map(|y| y.clone().unwrap_or(HashSet::new())).collect()).collect::<Vec<Vec<HashSet<usize>>>>()),
    ));
    input_data.push(("executionTime", MiniZincData::from(execution_times)));
    input_data.push((
        "invBandwidthPerChannel",
        MiniZincData::from(
            communications
                .iter()
                .map(|ce| {
                    m.partitioned_tiled_multicore
                        .hardware
                        .communication_elements_bit_per_sec_per_channel
                        .get(ce)
                        .map(|x| {
                            (average_max as f64 / x / discrete_max as f64 / memory_scale as f64)
                                .ceil() as i32
                        })
                        .unwrap_or(0)
                })
                .collect::<Vec<i32>>(),
        ),
    ));
    input_data.push((
        "nPareto",
        MiniZincData::from(current_solutions.len() as u64),
    ));
    input_data.push((
        "previousSolutions",
        MiniZincData::from(
            current_solutions
                .iter()
                .map(|s| {
                    let mut objs = vec![];
                    objs.push((*s.objectives.get("nUsedPEs").unwrap_or(&0.0)) as u64);
                    for p in &all_processes {
                        objs.push(
                            *s.objectives
                                .get(&format!("invThroughput({})", p))
                                .unwrap_or(&0.0) as u64,
                        );
                    }
                    objs
                })
                .collect::<Vec<Vec<u64>>>(),
        ),
    ));
    input_data.push(("connected", MiniZincData::from(connected)));

    let temp_dir = std::env::temp_dir().join("idesyde").join("minizinc");
    let model_file = temp_dir.join("ADDPTM.mzn");
    let data_file = temp_dir.join("ADDPTM.json");
    std::fs::create_dir_all(&temp_dir).expect("Could not create the temporary directory");
    std::fs::write(&model_file, AADPTM_MZN).expect("Could not write the model file");
    std::fs::write(&data_file, to_mzn_input(input_data)).expect("Could not write the data file");
    match std::process::Command::new("minizinc")
    .arg("-f")
    .arg("--solver")
    .arg(explorer_name)
            .arg("--json-stream")
            .arg("--output-mode")
            .arg("json")
            .arg(data_file.as_path())
            .arg(model_file.as_path())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::null())
            .spawn() {
        Ok(proc) => {
            if let Some(stdout) = proc.stdout {
                let bufreader = BufReader::new(stdout);
                let input = m.clone();
                return Arc::new(Mutex::new(
                    bufreader
                        .lines()
                        .take_while(|l| l.is_ok())
                        // .inspect(|l| {
                        //     if let Ok(line) = l {
                        //         println!("{}", line);
                        //     }
                        // })
                        .flat_map(move |line_r| {
                            if let Ok(line) = line_r {
                                if line.contains("UNSATISFIABLE") {
                                    return None;
                                } else if line.contains("output") {
                                    let mzn_out: MiniZincSolutionOutput<AADPTMMznOutput> =
                                        serde_json::from_str(line.as_str())
                                            .expect("Should not fail to parse the output of minizinc");
                                    let mut explored = input.clone();
                                    if let Some(mzn_vars) = mzn_out.output.get("json") {
                                        let list_schedulers_mapping: HashMap<String, String> = mzn_vars
                                            .process_mapping
                                            .iter()
                                            .enumerate()
                                            .filter(|(_, r)| **r < list_schedulers.len() as u64)
                                            .map(|(p, r)| {
                                                (
                                                    all_processes[p].clone(),
                                                    list_schedulers[*r as usize].clone(),
                                                )
                                            })
                                            .collect();
                                        explored.processes_to_runtime_scheduling =
                                            list_schedulers_mapping.clone();
                                        explored.processes_to_memory_mapping = mzn_vars
                                            .process_mapping
                                            .iter()
                                            .enumerate()
                                            .map(|(p, r)| {
                                                (
                                                    all_processes[p].clone(),
                                                    memories[*r as usize].clone(),
                                                )
                                            })
                                            .collect();
                                        explored.buffer_to_memory_mappings = mzn_vars
                                            .buffers_mapping
                                            .iter()
                                            .enumerate()
                                            .map(|(b, i)| {
                                                (all_buffers[b].clone(), memories[*i as usize].clone())
                                            })
                                            .collect();
                                        let firings: Vec<(&str, u64)> = all_firings_actor
                                            .iter()
                                            .map(|s| s.as_str())
                                            .zip(all_firings_instances.iter().map(|i| i.to_owned()))
                                            .collect();
                                        explored.super_loop_schedules = list_schedulers
                                            .iter()
                                            .map(|pe| {
                                                let mut looplist: Vec<(String, u64)> = firings
                                                    .iter()
                                                    .zip(mzn_vars.firings_ordering.iter())
                                                    .filter(|((a, _), _)| {
                                                        list_schedulers_mapping
                                                            .get(*a)
                                                            .map(|x| pe == x)
                                                            .unwrap_or(false)
                                                    })
                                                    .map(|((a, _), idx)| (a.to_string(), *idx))
                                                    .collect();
                                                looplist.sort_by_key(|(_, idx)| *idx);
                                                (
                                                    pe.clone(),
                                                    looplist.into_iter().map(|(a, _)| a).collect(),
                                                )
                                            })
                                            .collect();
                                        explored.processing_elements_to_routers_reservations =
                                            list_schedulers
                                                .iter()
                                                .zip(mzn_vars.communication_reservation.iter())
                                                .map(|(pe, res)| {
                                                    (
                                                        pe.clone(),
                                                        communications
                                                            .iter()
                                                            .zip(res.iter())
                                                            .filter(|(_, r)| **r > 0)
                                                            .map(|(c, r)| (c.clone(), *r as u16))
                                                            .collect(),
                                                    )
                                                })
                                                .collect();
                                        let mut objs = HashMap::new();
                                        objs.insert("nUsedPEs".to_string(), mzn_vars.n_used_pes as f64);
                                        let inv_throughputs = explored.recompute_throughputs();
                                        for (p, inv) in &inv_throughputs {
                                            objs.insert(
                                                format!("invThroughput({})", p),
                                                *inv as f64,
                                            );
                                        }
                                        return Some(ExplorationSolution {
                                            solved: Arc::new(explored),
                                            objectives: objs,
                                        });
                                    }
                                }
                            }
                            None
                        }),
                ));
            }
        }
        _ => (),
    };
    Arc::new(Mutex::new(std::iter::empty::<ExplorationSolution>()))
}

pub fn make_module() -> RustEmbeddedModule {
    RustEmbeddedModule::builder()
        .unique_identifier("MiniZincModule".to_string())
        .identification_rules(vec![])
        // .explorers(vec![Arc::new(MiniZincGecodeExplorer)])
        .explorers(vec![Arc::new(MiniZincORToolsExplorer)])
        .build()
        .expect("Failed to build MiniZincModule. Should never happen.")
}
