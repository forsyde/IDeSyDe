use std::collections::{HashMap, HashSet};

use idesyde_core::{
    headers::DecisionModelHeader, impl_decision_model_standard_parts, DecisionModel, DesignModel,
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

#[derive(
    Debug,
    PartialEq,
    Serialize,
    Deserialize,
    Clone,
    JsonSchema,
    Debug,
    PartialEq,
    Serialize,
    Deserialize,
    Clone,
    JsonSchema,
)]
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

#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AsynchronousAperiodicDataflow {
    pub processes: HashSet<String>,
    pub job_graph_must_follow_src: Vec<(String, u32)>,
    pub job_graph_must_follow_dst: Vec<(String, u32)>,
    pub job_graph_may_follow_src: Vec<(String, u32)>,
    pub job_graph_may_follow_dst: Vec<(String, u32)>,
}

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
