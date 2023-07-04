use std::collections::{HashMap, HashSet};

use idesyde_core::{
    headers::DecisionModelHeader, impl_decision_model_standard_parts, DecisionModel,
};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

#[derive(Debug, PartialEq, Eq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct CommunicatingAndTriggeredReactiveWorkload {
    pub tasks: Vec<String>,
    pub task_sizes: Vec<u32>,
    pub task_computational_needs: Vec<HashMap<String, HashMap<String, u32>>>,
    pub data_channels: Vec<String>,
    pub data_channel_sizes: Vec<u32>,
    pub data_graph_src: Vec<String>,
    pub data_graph_dst: Vec<String>,
    pub data_graph_message_size: Vec<u32>,
    pub periodic_sources: Vec<String>,
    pub periods_numerator: Vec<u32>,
    pub periods_denominator: Vec<u32>,
    pub offsets_numerator: Vec<u32>,
    pub offsets_denominator: Vec<u32>,
    pub upsamples: Vec<String>,
    pub upsample_repetitive_holds: Vec<u32>,
    pub upsample_initial_holds: Vec<u32>,
    pub downsamples: Vec<String>,
    pub downample_repetitive_skips: Vec<u32>,
    pub downample_initial_skips: Vec<u32>,
    pub trigger_graph_src: Vec<String>,
    pub trigger_graph_dst: Vec<String>,
    pub has_or_trigger_semantics: HashSet<String>,
}

impl DecisionModel for CommunicatingAndTriggeredReactiveWorkload {
    impl_decision_model_standard_parts!(CommunicatingAndTriggeredReactiveWorkload);

    fn header(&self) -> idesyde_core::headers::DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.tasks.iter().map(|x| x.to_owned()));
        elems.extend(self.data_channels.iter().map(|x| x.to_owned()));
        elems.extend(self.periodic_sources.iter().map(|x| x.to_owned()));
        elems.extend(self.upsamples.iter().map(|x| x.to_owned()));
        elems.extend(self.downsamples.iter().map(|x| x.to_owned()));
        for i in 0..self.data_graph_src.len() {
            elems.insert(format!(
                "{}={}:{}-{}:{}",
                self.data_graph_message_size[i],
                self.data_graph_src[i],
                "",
                self.data_graph_dst[i],
                ""
            ));
        }
        for i in 0..self.trigger_graph_src.len() {
            elems.insert(format!(
                "{}={}:{}-{}:{}",
                "trigger", self.trigger_graph_src[i], "", self.trigger_graph_dst[i], ""
            ));
        }
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}

/// Decision model for synchronous dataflow graphs.
///
/// This decision model encodes a synchronous dataflow graphs without its explicit topology matrix,  also known as balance matrix in some newer texts.
/// This is achieved by encoding the graph as (A + C, E) where A is the set of actors, and C is the set of channels.
/// Every edge in E connects an actor to a channel or a channel to an
/// actor, i.e. e = (a,c,m) or e = (c,a.m) where m is the amount of token produced or consumed.
/// For example, if e = (a, c, 2), then the edge e is the production of 2
/// tokens from the actor a to channel c.
///
/// This decision model is already analised, and provides the repetition vector for the SDF graphs contained as well
/// as a schedule if these SDF graphs are consistent.
///
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct SDFApplication {
    pub actors_identifiers: Vec<String>,
    pub channels_identifiers: Vec<String>,
    pub topology_srcs: Vec<String>,
    pub topology_dsts: Vec<String>,
    pub topology_edge_value: Vec<i64>,
    pub actor_sizes: HashMap<String, u64>,
    pub actor_computational_needs: HashMap<String, HashMap<String, HashMap<String, u64>>>,
    pub channel_num_initial_tokens: HashMap<String, i64>,
    pub channel_token_sizes: HashMap<String, u64>,
    pub minimum_actor_throughputs: HashMap<String, f64>,
    pub repetition_vector: Vec<String>,
    pub topological_and_heavy_job_ordering: Vec<String>,
}

impl DecisionModel for SDFApplication {
    impl_decision_model_standard_parts!(SDFApplication);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.actors_identifiers.iter().map(|x| x.to_owned()));
        elems.extend(self.channels_identifiers.iter().map(|x| x.to_owned()));
        for i in 0..self.topology_srcs.len() {
            elems.insert(format!(
                "{}={}:{}-{}:{}",
                self.topology_edge_value[i], self.topology_srcs[i], "", self.topology_dsts[i], ""
            ));
        }
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}

#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct TiledMultiCore {
    pub processors: Vec<String>,
    pub memories: Vec<String>,
    pub network_interfaces: Vec<String>,
    pub routers: Vec<String>,
    pub interconnect_topology_srcs: Vec<String>,
    pub interconnect_topology_dsts: Vec<String>,
    pub processors_provisions: HashMap<String, HashMap<String, HashMap<String, f64>>>,
    pub processors_frequency: HashMap<String, u64>,
    pub tile_memory_sizes: HashMap<String, u64>,
    pub communication_elements_max_channels: HashMap<String, u32>,
    pub communication_elements_bit_per_sec_per_channel: HashMap<String, f64>,
    pub pre_computed_paths: HashMap<String, HashMap<String, Vec<String>>>,
}

impl DecisionModel for TiledMultiCore {
    impl_decision_model_standard_parts!(TiledMultiCore);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processors.iter().map(|x| x.to_owned()));
        elems.extend(self.memories.iter().map(|x| x.to_owned()));
        elems.extend(self.network_interfaces.iter().map(|x| x.to_owned()));
        elems.extend(self.routers.iter().map(|x| x.to_owned()));
        for i in 0..self.interconnect_topology_srcs.len() {
            elems.insert(format!(
                "{}={}:{}-{}:{}",
                "interconnect",
                self.interconnect_topology_srcs[i],
                "",
                self.interconnect_topology_dsts[i],
                ""
            ));
        }
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}

#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct RuntimesAndProcessors {
    pub runtimes: Vec<String>,
    pub processors: Vec<String>,
    pub runtime_host: HashMap<String, String>,
    pub processor_affinities: HashMap<String, String>,
    pub is_bare_metal: Vec<bool>,
    pub is_fixed_priority: Vec<bool>,
    pub is_earliest_deadline_first: Vec<bool>,
    pub is_cyclic_executive: Vec<bool>,
}

impl DecisionModel for RuntimesAndProcessors {
    impl_decision_model_standard_parts!(RuntimesAndProcessors);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processors.iter().map(|x| x.to_owned()));
        elems.extend(self.runtimes.iter().map(|x| x.to_owned()));
        for (sched, pe) in &self.runtime_host {
            elems.insert(format!("{}={}:{}-{}:{}", "host", sched, "", pe, ""));
        }
        for (pe, sched) in &self.processor_affinities {
            elems.insert(format!("{}={}:{}-{}:{}", "scheduler", pe, "", sched, ""));
        }
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}

#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct PartitionedTiledMulticore {
    pub hardware: TiledMultiCore,
    pub runtimes: RuntimesAndProcessors,
}

impl DecisionModel for PartitionedTiledMulticore {
    impl_decision_model_standard_parts!(PartitionedTiledMulticore);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(
            self.hardware
                .header()
                .covered_elements
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.runtimes
                .header()
                .covered_elements
                .iter()
                .map(|x| x.to_owned()),
        );
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}

/// This decision model abstract asynchronous dataflow models that can be described
/// by a repeating job-graph of this asynchronous processes.
/// Two illustratives dataflow models fitting this category are synchronous dataflow models (despite the name)
/// and cyclo-static dataflow models.
///
/// Assumptions:
/// 1. the job graph is always ready to be executed; or, the model is aperiodic.
///
/// 2. executing the job graph as presented guarantees that the dataflow processes are live (never deadlocked).
///
/// 3. The job graph ois weakly connected. If you wish to have multiple "applications", you should generate
/// one decision model for each application.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AsynchronousAperiodicDataflow {
    pub processes: HashSet<String>,
    pub jobs_of_processes: Vec<String>,
    pub job_graph_src: Vec<u32>,
    pub job_graph_dst: Vec<u32>,
    pub job_graph_data_sent: Vec<Vec<u64>>,
    pub job_graph_data_read: Vec<Vec<u64>>,
    pub job_graph_buffer_name: Vec<Vec<String>>,
    pub buffer_max_sizes: HashMap<String, u64>,
    pub process_minimum_throughput: HashMap<String, f64>,
    pub process_path_maximum_latency: HashMap<String, HashMap<String, f64>>,
}

impl DecisionModel for AsynchronousAperiodicDataflow {
    impl_decision_model_standard_parts!(AsynchronousAperiodicDataflow);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(
            self.job_graph_buffer_name
                .iter()
                .flatten()
                .map(|x| x.to_owned()),
        );
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}
