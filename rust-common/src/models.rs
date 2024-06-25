use std::collections::{HashMap, HashSet};

use idesyde_core::{
    impl_decision_model_conversion, impl_decision_model_standard_parts, DecisionModel,
};
use petgraph::{
    algo::tarjan_scc, graph::NodeIndex, visit::{Bfs, EdgeRef, IntoNeighbors, NodeIndexable}, Direction::{Incoming, Outgoing}, Graph
};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

const NUMERICAL_RELATIVE_ERROR: f64 = 0.0001;

/// A model that abstracts concurrent processes where stimulus and dataflow are separate.
///
/// This abstraction encodes any workload as a combination of concurrent tasks and data channels.
/// The concurrent tasks can be activated in accordance to incoming periodic stimulus.
/// Whenever these are activated, they read their incoming data channels,
/// they propagate stimulus to outgoing concurrent tasks and write to outgoing channels.
/// The network of stimulus propagation can include downsampling and upsamling so that tasks
/// can working in a multi-rate fashion.
///
/// This model also has OR and AND triggering semantics.
/// If there are multiple incoming stimulae, and the stimulated element follows a OR semantic,
/// it it must be triggered with *each* incoming stimulae, and propagate them.
/// Otherwise, if the stimulated element follows a AND semantic, it only triggers *with all*
/// incoming stimulae, as if the stimulated element is waiting for all incoming stimulae to arrive.
///
/// ## Example model
///
/// The following diagram exemplifies all the possible connnections.
///
/// ```text
///             ┌──────────────┐     ┌──────┐
///             │Data channel 1├────►│Task 3│
///             └──────────────┘     └──────┘
///                ▲
///                │
/// ┌───────┐   ┌──┴───┐   ┌─────────────┐   ┌──────┐
/// │10 secs├──►│Task 1├──►│Upsample by 3├──►│Task 2│
/// └───────┘   └┬─┬───┘   └─────────────┘   └──────┘
///              │ │
///              │ │ ┌───────────────┐
///              │ └►│Downsample by 2├──────┐
///              │   └───────────────┘      ▼
///              │                       ┌──────┐
///              │                       │Task 4│
///              │                       └──────┘
///              │   ┌──────────────┐       ▲
///              └──►│Data channel 2├───────┘
///                  └──────────────┘
/// ```
/// Task 1 must be executed every 10 seconds as it is directly stimulated by a 10 second source.
/// Task 2 executes every 10/3 seconds since it is upsampled from Task 1; yet, Tasks 1 and 2 do *not* communicate.
/// Task 3 has no periodic activation whatsover in this diagram, but it is known that it recieved data from Task 1 via Data Channel 1.
/// Task 4 executes every 20 seconds as downsampled from Task 1 *and* consumed data produced by it every 20 seconds;
/// naturally, the data received is from every other execution of Task 1.
///
/// ## References
///
/// This model is a small abstraction on top of the extended-dependency periodic task model proposed by Forget
/// and seemingly used by the PRELUDE synchornous programming language in the following text:
///
/// J. Forget, F. Boniol, E. Grolleau, D. Lesens, and C. Pagetti, ‘Scheduling Dependent Periodic Tasks without Synchronization Mechanisms’,
/// in 2010 16th IEEE Real-Time and Embedded Technology and Applications Symposium, Apr. 2010, pp. 301–310. doi: 10.1109/RTAS.2010.26.
///
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

impl_decision_model_conversion!(CommunicatingAndTriggeredReactiveWorkload);
impl DecisionModel for CommunicatingAndTriggeredReactiveWorkload {
    impl_decision_model_standard_parts!(CommunicatingAndTriggeredReactiveWorkload);

    fn part(&self) -> HashSet<String> {
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
        elems
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
    pub actor_minimum_throughputs: HashMap<String, f64>,
    pub actors_identifiers: HashSet<String>,
    pub chain_maximum_latency: HashMap<String, HashMap<String, f64>>,
    pub channel_token_sizes: HashMap<String, u64>,
    pub channels_identifiers: HashSet<String>,
    pub self_concurrent_actors: HashSet<String>,
    pub topology_channel_names: Vec<HashSet<String>>,
    pub topology_consumption: Vec<u32>,
    pub topology_dsts: Vec<String>,
    pub topology_initial_tokens: Vec<u32>,
    pub topology_production: Vec<u32>,
    pub topology_srcs: Vec<String>,
    pub topology_token_size_in_bits: Vec<u64>,
}

impl_decision_model_conversion!(SDFApplication);
impl DecisionModel for SDFApplication {
    impl_decision_model_standard_parts!(SDFApplication);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.actors_identifiers.iter().map(|x| x.to_owned()));
        elems.extend(self.channels_identifiers.iter().map(|x| x.to_owned()));
        // for i in 0..self.topology_srcs.len() {
        //     elems.insert(format!(
        //         "({}, {}, {})={}:{}-{}:{}",
        //         self.topology_consumption[i],
        //         self.topology_production[i],
        //         self.topology_initial_tokens[i],
        //         self.topology_srcs[i],
        //         "",
        //         self.topology_dsts[i],
        //         ""
        //     ));
        // }
        elems
    }
}

/// Decision model for analysed synchronous dataflow graphs.
///
/// Aside from the same information in the original SDF application,
/// it also includes liveness information like its repetition vector.
///
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AnalysedSDFApplication {
    pub sdf_application: SDFApplication,
    pub repetition_vector: HashMap<String, u64>,
    pub periodic_admissible_static_schedule: Vec<String>,
}

impl_decision_model_conversion!(AnalysedSDFApplication);
impl DecisionModel for AnalysedSDFApplication {
    impl_decision_model_standard_parts!(AnalysedSDFApplication);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.sdf_application.part().iter().map(|x| x.to_owned()));
        elems
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

impl_decision_model_conversion!(TiledMultiCore);
impl DecisionModel for TiledMultiCore {
    impl_decision_model_standard_parts!(TiledMultiCore);

    fn part(&self) -> HashSet<String> {
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
        elems
    }
}

/// A decision model capturing the memory mappable platform abstraction.
///
/// This type of platform is what one would expect from most COTS platforms
/// and hardware designs, which completely or partially follows a von neumman
/// architecture. This means that the storage elements store both data and instructions
/// and the processors access them going through the communication elements; the latter
/// that form the 'interconnect'.
#[derive(Debug, Deserialize, Serialize, Clone, PartialEq, JsonSchema)]
pub struct MemoryMappableMultiCore {
    pub processing_elems: HashSet<String>,
    pub storage_elems: HashSet<String>,
    pub communication_elems: HashSet<String>,
    pub topology_srcs: Vec<String>,
    pub topology_dsts: Vec<String>,
    pub processors_frequency: HashMap<String, u64>,
    pub processors_provisions: HashMap<String, HashMap<String, HashMap<String, f64>>>,
    pub storage_sizes: HashMap<String, u64>,
    pub communication_elements_max_channels: HashMap<String, u32>,
    pub communication_elements_bit_per_sec_per_channel: HashMap<String, f64>,
    pub pre_computed_paths: HashMap<String, HashMap<String, Vec<String>>>,
}

impl_decision_model_conversion!(MemoryMappableMultiCore);
impl DecisionModel for MemoryMappableMultiCore {
    impl_decision_model_standard_parts!(MemoryMappableMultiCore);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processing_elems.iter().map(|x| x.to_owned()));
        elems.extend(self.storage_elems.iter().map(|x| x.to_owned()));
        elems.extend(self.communication_elems.iter().map(|x| x.to_owned()));
        for i in 0..self.topology_dsts.len() {
            elems.insert(format!(
                "{}:{}-{}:{}",
                self.topology_srcs[i], "", self.topology_dsts[i], ""
            ));
        }
        elems
    }
}

/// A decision model capturing the memory mappable platform abstraction.
///
/// This type of platform is what one would expect from most COTS platforms
/// and hardware designs, which completely or partially follows a von neumman
/// architecture. This means that the storage elements store both data and instructions
/// and the processors access them going through the communication elements; the latter
/// that form the 'interconnect'. In addition to standard software processing elements,
/// this decision model also includes programmable logic capacities on the platform.
#[derive(Debug, Deserialize, Serialize, Clone, PartialEq, JsonSchema)]
pub struct MemoryMappableMulticoreWithPL {
    pub processing_elems: HashSet<String>,
    pub programmable_logic_elems: HashSet<String>,
    pub pl_module_available_areas: HashMap<String, u32>,
    pub storage_elems: HashSet<String>,
    pub communication_elems: HashSet<String>,
    pub topology_srcs: Vec<String>,
    pub topology_dsts: Vec<String>,
    pub processors_frequency: HashMap<String, u64>,
    pub processors_provisions: HashMap<String, HashMap<String, HashMap<String, f64>>>,
    pub storage_sizes: HashMap<String, u64>,
    pub communication_elements_max_channels: HashMap<String, u32>,
    pub communication_elements_bit_per_sec_per_channel: HashMap<String, f64>,
    pub pre_computed_paths: HashMap<String, HashMap<String, Vec<String>>>,
}

impl_decision_model_conversion!(MemoryMappableMulticoreWithPL);
impl DecisionModel for MemoryMappableMulticoreWithPL {
    impl_decision_model_standard_parts!(MM_MCoreAndPL);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processing_elems.iter().map(|x: &String| x.to_owned()));
        elems.extend(self.storage_elems.iter().map(|x: &String| x.to_owned()));
        elems.extend(
            self.programmable_logic_elems
                .iter()
                .map(|x: &String| x.to_owned()),
        );
        elems.extend(
            self.communication_elems
                .iter()
                .map(|x: &String| x.to_owned()),
        );
        for i in 0..self.topology_dsts.len() {
            elems.insert(format!(
                "{}:{}-{}:{}",
                self.topology_srcs[i], "", self.topology_dsts[i], ""
            ));
        }
        elems
    }
}

impl MemoryMappableMulticoreWithPL {
    pub fn platform_as_graph(&self) -> Graph<String, ()> {
        let mut graph = Graph::new();
        let mut nodes = HashMap::new();
        for pe in &self.processing_elems {
            nodes.insert(pe.clone(), graph.add_node(pe.clone()));
        }
        for pe in &self.programmable_logic_elems {
            nodes.insert(pe.clone(), graph.add_node(pe.clone()));
        }
        for mem in &self.storage_elems {
            nodes.insert(mem.clone(), graph.add_node(mem.clone()));
        }
        for ce in &self.communication_elems {
            nodes.insert(ce.clone(), graph.add_node(ce.clone()));
        }
        for (src, dst) in self.topology_srcs.iter().zip(self.topology_dsts.iter()) {
            let src_node = nodes.get(src).unwrap();
            let dst_node = nodes.get(dst).unwrap();
            graph.add_edge(src_node.clone(), dst_node.clone(), ());
        }
        graph
    }
}

/// A decision model capturing the binding between procesing element and runtimes.
///
/// A runtime here is used in a loose sense: it can be simply a programmable bare-metal
/// environment. The assumption is that every runtime has one processing element host
/// and all processing elements might have only one runtime that it is affine to.
/// A processing element having affinity to a runtime simply means that this
/// runtime is managing the processing element according to any policy.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct RuntimesAndProcessors {
    pub runtimes: HashSet<String>,
    pub processors: HashSet<String>,
    pub runtime_host: HashMap<String, String>,
    pub processor_affinities: HashMap<String, String>,
    pub is_bare_metal: HashSet<String>,
    pub is_fixed_priority: HashSet<String>,
    pub is_preemptive: HashSet<String>,
    pub is_earliest_deadline_first: HashSet<String>,
    pub is_super_loop: HashSet<String>,
}

impl_decision_model_conversion!(RuntimesAndProcessors);
impl DecisionModel for RuntimesAndProcessors {
    impl_decision_model_standard_parts!(RuntimesAndProcessors);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processors.iter().map(|x| x.to_owned()));
        elems.extend(self.runtimes.iter().map(|x| x.to_owned()));
        for (sched, pe) in &self.runtime_host {
            elems.insert(format!("{}={}:{}-{}:{}", "host", sched, "", pe, ""));
        }
        for (pe, sched) in &self.processor_affinities {
            elems.insert(format!("{}={}:{}-{}:{}", "scheduler", pe, "", sched, ""));
        }
        elems
    }
}

/// A decision model that captures a paritioned-scheduled tiled multicore machine
///
/// This means that every processing element hosts and has affinity for one and only one runtime element.
/// This runtime element can execute according to any scheduling policy, but it must control only
/// its host.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct PartitionedTiledMulticore {
    pub hardware: TiledMultiCore,
    pub runtimes: RuntimesAndProcessors,
}

impl_decision_model_conversion!(PartitionedTiledMulticore);
impl DecisionModel for PartitionedTiledMulticore {
    impl_decision_model_standard_parts!(PartitionedTiledMulticore);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.hardware.part().iter().map(|x| x.to_owned()));
        elems.extend(self.runtimes.part().iter().map(|x| x.to_owned()));
        elems
    }
}

/// A decision model that captures a paritioned-scheduled memory mappable multicore machine
///
/// This means that every processing element hosts and has affinity for one and only one runtime element.
/// This runtime element can execute according to any scheduling policy, but it must control only
/// its host.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct PartitionedMemoryMappableMulticore {
    pub hardware: MemoryMappableMultiCore,
    pub runtimes: RuntimesAndProcessors,
}

impl_decision_model_conversion!(PartitionedMemoryMappableMulticore);
impl DecisionModel for PartitionedMemoryMappableMulticore {
    impl_decision_model_standard_parts!(PartitionedMemoryMappableMulticore);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.hardware.part().iter().map(|x| x.to_owned()));
        elems.extend(self.runtimes.part().iter().map(|x| x.to_owned()));
        elems
    }
}

/// A decision model that captures a paritioned-scheduled memory mappable multicore machine
///
/// This means that every processing element hosts and has affinity for one and only one runtime element.
/// This runtime element can execute according to any scheduling policy, but it must control only
/// its host.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct PartitionedMemoryMappableMulticoreAndPL {
    pub hardware: MemoryMappableMulticoreWithPL,
    pub runtimes: RuntimesAndProcessors,
}

impl_decision_model_conversion!(PartitionedMemoryMappableMulticoreAndPL);
impl DecisionModel for PartitionedMemoryMappableMulticoreAndPL {
    impl_decision_model_standard_parts!(PartitionedMemoryMappableMulticoreAndPL);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.hardware.part().iter().map(|x| x.to_owned()));
        elems.extend(self.runtimes.part().iter().map(|x| x.to_owned()));
        elems
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
/// 3. The job graph is weakly connected. If you wish to have multiple "applications", you should generate
/// one decision model for each application.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AperiodicAsynchronousDataflow {
    pub processes: HashSet<String>,
    pub buffers: HashSet<String>,
    pub buffer_max_size_in_bits: HashMap<String, u64>,
    pub buffer_token_size_in_bits: HashMap<String, u64>,
    pub process_put_in_buffer_in_bits: HashMap<String, HashMap<String, u64>>,
    pub process_get_from_buffer_in_bits: HashMap<String, HashMap<String, u64>>,
    pub job_graph_name: Vec<String>,
    pub job_graph_instance: Vec<u64>,
    pub job_graph_src_name: Vec<String>,
    pub job_graph_dst_name: Vec<String>,
    pub job_graph_src_instance: Vec<u64>,
    pub job_graph_dst_instance: Vec<u64>,
    pub job_graph_is_strong_precedence: Vec<bool>,
    pub process_minimum_throughput: HashMap<String, f64>,
    pub process_path_maximum_latency: HashMap<String, HashMap<String, f64>>,
}

impl_decision_model_conversion!(AperiodicAsynchronousDataflow);
impl DecisionModel for AperiodicAsynchronousDataflow {
    impl_decision_model_standard_parts!(AsynchronousAperiodicDataflow);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.buffers.iter().map(|x| x.to_string()));
        elems
    }
}

impl AperiodicAsynchronousDataflow {
    pub fn job_follows(&self) -> HashMap<(&str, u64), Vec<(&str, u64)>> {
        let mut follows = HashMap::new();
        let mut firings_graph: Graph<(&str, u64), (), petgraph::Directed> = Graph::new();
        let mut firings_graph_idx = HashMap::new();
        for (a, q) in self
            .job_graph_name
            .iter()
            .zip(self.job_graph_instance.iter())
        {
            let _ = firings_graph_idx
                .insert((a.as_str(), *q), firings_graph.add_node((a.as_str(), *q)));
        }
        for i in 0..self.job_graph_src_name.len() {
            let src_a = self.job_graph_src_name[i].as_str();
            let src_q = self.job_graph_src_instance[i];
            let dst_a = self.job_graph_dst_name[i].as_str();
            let dst_q = self.job_graph_dst_instance[i];
            let src_index = firings_graph_idx.get(&(src_a, src_q)).unwrap();
            let dst_index = firings_graph_idx.get(&(dst_a, dst_q)).unwrap();
            firings_graph.add_edge(*src_index, *dst_index, ());
        }
        let firings_graph_toposort = petgraph::algo::toposort(&firings_graph, None)
            .expect("Firings graph has a cycle. Should never happen.");
        let (res, revmap) = petgraph::algo::tred::dag_to_toposorted_adjacency_list(
            &firings_graph,
            &firings_graph_toposort,
        );
        let (_, firings_graph_closure) = petgraph::algo::tred::dag_transitive_reduction_closure::<
            (),
            petgraph::graph::DefaultIx,
        >(&res);
        for (src_a, src_q) in self
            .job_graph_name
            .iter()
            .zip(self.job_graph_instance.iter())
        {
            let src_index = firings_graph_idx.get(&(src_a.as_str(), *src_q)).unwrap();
            let mut follows_vec: Vec<(&str, u64)> = vec![];
            for e in firings_graph_closure.neighbors(revmap[src_index.index()]) {
                if let Some(og_idx) = revmap.iter().position(|x| *x == e) {
                    if let Some(f) = firings_graph
                        .node_weight(firings_graph.from_index(og_idx))
                        .map(|x| *x)
                    {
                        follows_vec.push(f);
                    };
                };
            }
            follows.insert((src_a.as_str(), *src_q), follows_vec);
        }
        follows
    }
}

/// A decision model to hold computation times between processsables and processing elements.
///
/// As the decision model stores these computation in associative arrays (maps), the lack
/// of an association between a processable and a processing element means that
/// this processable _cannot_ be executed in the processing element.
///
/// In order to maintain the precision as pristine as possible, the values are stored
/// in a "scaled" manner. That is, there is a scaling factor that denotes the denominator
/// in which all the stored values must be divided by. This enables us to move the
/// computational numbers around as integers. Therefore, for any value in this
/// decision model:
///
/// actual_value = integer_value / scale_factor
///
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct InstrumentedComputationTimes {
    pub processes: HashSet<String>,
    pub processing_elements: HashSet<String>,
    pub best_execution_times: HashMap<String, HashMap<String, u64>>,
    pub average_execution_times: HashMap<String, HashMap<String, u64>>,
    pub worst_execution_times: HashMap<String, HashMap<String, u64>>,
    pub scale_factor: u64,
}

impl_decision_model_conversion!(InstrumentedComputationTimes);
impl DecisionModel for InstrumentedComputationTimes {
    impl_decision_model_standard_parts!(InstrumentedComputationTimes);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.processing_elements.iter().map(|x| x.to_string()));
        elems
    }
}

/// A decision model to hold memory requirements for processes when executing in processing elements.
///
/// As the decision model stores these memory requirements in associative arrays (maps), the lack
/// of an association between a process and a processing element means that
/// this process _cannot_ be executed in the processing element.
///
///
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct InstrumentedMemoryRequirements {
    pub processes: HashSet<String>,
    pub channels: HashSet<String>,
    pub processing_elements: HashSet<String>,
    pub memory_requirements: HashMap<String, HashMap<String, u64>>,
}

impl_decision_model_conversion!(InstrumentedMemoryRequirements);
impl DecisionModel for InstrumentedMemoryRequirements {
    impl_decision_model_standard_parts!(InstrumentedMemoryRequirements);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.processing_elements.iter().map(|x| x.to_string()));
        elems
    }
}

/// A decision model to hold the required area that a hardware implementation needs.
///
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct HardwareImplementationArea {
    pub processes: HashSet<String>,
    pub programmable_areas: HashSet<String>,
    pub required_areas: HashMap<String, HashMap<String, u64>>,
    pub required_resources: HashMap<String, HashMap<String, HashMap<String, u64>>>,
    pub provided_resources: HashMap<String, HashMap<String, u64>>,
    pub latencies_numerators: HashMap<String, HashMap<String, u64>>,
    pub latencies_denominators: HashMap<String, HashMap<String, u64>>,
}

impl_decision_model_conversion!(HardwareImplementationArea);
impl DecisionModel for HardwareImplementationArea {
    impl_decision_model_standard_parts!(InstrumentedMemoryRequirements);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.programmable_areas.iter().map(|x| x.to_string()));
        elems
    }
}

/// A decision model that combines one type of application, platform and information to bind them.
///
/// The assumptions of this decision model are:
///  1. For every process, there is at least one processing element in the platform that can run it.
///     Otherwise, even the trivial mapping is impossible.
///  2. Super loop schedules are self-timed and stall the processing element that is hosting them.
///     That is, if we have a poor schedule, the processing element will get "blocked" often.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AperiodicAsynchronousDataflowToPartitionedTiledMulticore {
    pub aperiodic_asynchronous_dataflows: Vec<AperiodicAsynchronousDataflow>,
    pub partitioned_tiled_multicore: PartitionedTiledMulticore,
    pub instrumented_computation_times: InstrumentedComputationTimes,
    pub instrumented_memory_requirements: InstrumentedMemoryRequirements,
    pub processes_to_runtime_scheduling: HashMap<String, String>,
    pub processes_to_memory_mapping: HashMap<String, String>,
    pub buffer_to_memory_mappings: HashMap<String, String>,
    pub super_loop_schedules: HashMap<String, Vec<String>>,
    pub processing_elements_to_routers_reservations: HashMap<String, HashMap<String, u16>>,
}

impl AperiodicAsynchronousDataflowToPartitionedTiledMulticore {
    pub fn get_max_discrete_value(&self) -> u64 {
        let biggest_path: u64 = self
            .partitioned_tiled_multicore
            .hardware
            .pre_computed_paths
            .values()
            .flat_map(|dsts| dsts.values().map(|path| path.len() as u64))
            .max()
            .unwrap_or(0)
            + 1;
        let number_jobs: u64 = self
            .aperiodic_asynchronous_dataflows
            .iter()
            .map(|app| app.processes.len() as u64)
            .sum();
        let num_mappables: u64 = self.partitioned_tiled_multicore.hardware.processors.len() as u64;
        let relative_constant = (-(NUMERICAL_RELATIVE_ERROR.log2().ceil())) as u32;
        let problem_constant = (biggest_path * number_jobs * num_mappables) as f64;
        let resolution = (problem_constant.log2().ceil() as u32) + relative_constant;
        2u64.pow(resolution)
    }

    pub fn get_max_average_execution_time(&self) -> f32 {
        let original_max_pes = self
            .instrumented_computation_times
            .average_execution_times
            .iter()
            .flat_map(|(_, times)| {
                times
                    .values()
                    .map(|x| *x as f32 / self.instrumented_computation_times.scale_factor as f32)
            })
            .reduce(f32::max)
            .unwrap_or(0.0);
        let original_max_traversal = self
            .partitioned_tiled_multicore
            .hardware
            .communication_elements_bit_per_sec_per_channel
            .values()
            .map(|x| 1.0 / x)
            .reduce(f64::max)
            .unwrap_or(0.0) as f32;
        original_max_pes.max(original_max_traversal)
    }

    pub fn get_memory_scale_factor(&self) -> u64 {
        *self
            .instrumented_memory_requirements
            .memory_requirements
            .values()
            .flat_map(|x| x.values())
            .chain(
                self.partitioned_tiled_multicore
                    .hardware
                    .tile_memory_sizes
                    .values(),
            )
            .chain(self.aperiodic_asynchronous_dataflows.iter().flat_map(
                |app: &AperiodicAsynchronousDataflow| app.buffer_max_size_in_bits.values(),
            ))
            .chain(self.aperiodic_asynchronous_dataflows.iter().flat_map(
                |app: &AperiodicAsynchronousDataflow| {
                    app.process_get_from_buffer_in_bits
                        .values()
                        .flat_map(|x| x.values())
                },
            ))
            .chain(self.aperiodic_asynchronous_dataflows.iter().flat_map(
                |app: &AperiodicAsynchronousDataflow| {
                    app.process_get_from_buffer_in_bits
                        .values()
                        .flat_map(|x| x.values())
                },
            ))
            .filter(|x| x > &&0)
            .min()
            .unwrap_or(&1)
    }

    pub fn recompute_throughputs(&self) -> HashMap<String, f64> {
        let all_actors: HashSet<String> = self.aperiodic_asynchronous_dataflows.iter().flat_map(|app| app.processes.iter()).map(|a| a.to_owned()).collect();
        let mut full_graph: Graph<(&str, u64), f64> = petgraph::Graph::new();
        let mut jobs_to_idx: HashMap<(&str, u64), NodeIndex> = HashMap::new();
        let mut messages_max_idx: HashMap<&str, u64> = self
            .aperiodic_asynchronous_dataflows
            .iter()
            .flat_map(|app| app.buffers.iter())
            .map(|b| (b.as_str(), 1u64))
            .collect();
        let actor_times: HashMap<&str, f64> = self.aperiodic_asynchronous_dataflows.iter().flat_map(|app| 
            app.processes.iter().map(|a| {
                let tile = self
                        .processes_to_runtime_scheduling
                        .get(a)
                        .and_then(|x| {
                            self.partitioned_tiled_multicore
                                .runtimes
                                .runtime_host
                                .get(x)
                        })
                        .expect(format!("No tile for source {}", a).as_str());
                let w = self.instrumented_computation_times
                                .average_execution_times
                                .get(a)
                                .and_then(|x| x.get(tile))
                                .map(|x| {
                                    *x as f64
                                        / self.instrumented_computation_times.scale_factor
                                            as f64
                                }).expect(format!("No computation time for source {} in {}", a, tile).as_str());
                (a.as_str(), w)
            })
        ).collect();
        let buffer_times: HashMap<&str, f64> = self.aperiodic_asynchronous_dataflows.iter().flat_map(|app|
            app.buffers.iter().map(|b| {
                let producer = app.processes.iter().find(|a| app.process_put_in_buffer_in_bits.get(*a).and_then(|x| x.get(b)).is_some()).expect("Buffer should have a producer.");
                let consumer = app.processes.iter().find(|a| app.process_get_from_buffer_in_bits.get(*a).and_then(|x| x.get(b)).is_some()).expect("Buffer should have a consumer.");
                let tile_src = self
                .processes_to_runtime_scheduling
                .get(producer)
                .and_then(|x| {
                    self.partitioned_tiled_multicore
                        .runtimes
                        .runtime_host
                        .get(x)
                })
                .expect(format!("No tile for source {}", producer).as_str());
                let tile_dst = self
                        .processes_to_runtime_scheduling
                        .get(consumer)
                        .and_then(|x| {
                            self.partitioned_tiled_multicore
                                .runtimes
                                .runtime_host
                                .get(x)
                        })
                        .expect(format!("No tile for source {}", consumer).as_str());
                let traversal_time = *app.process_put_in_buffer_in_bits.get(producer).and_then(|x| x.get(b)).unwrap_or(&0) as f64 * self
                                .partitioned_tiled_multicore
                                .hardware
                                .pre_computed_paths
                                .get(tile_src)
                                .and_then(|x| x.get(tile_dst))
                                .map(|path| {
                                    path.iter()
                                        .map(|ce| {
                                            *self
                                                .processing_elements_to_routers_reservations
                                                .get(tile_src)
                                                .and_then(|t| t.get(ce))
                                                .unwrap_or(&1) as f64 / 
                                                *self.partitioned_tiled_multicore
                                                .hardware
                                                .communication_elements_bit_per_sec_per_channel
                                                .get(ce)
                                                .unwrap_or(&1.0)
                                        })
                                        .reduce(|x, y| x.max(y))
                                        .unwrap_or(0.0)
                                })
                                .unwrap_or(0.0);
                (b.as_str(), traversal_time)
            })
        ).collect();
        for app in &self.aperiodic_asynchronous_dataflows {
            for (src, q) in app.job_graph_name.iter().zip(app.job_graph_instance.iter()) {
                jobs_to_idx.insert((src.as_str(), *q), full_graph.add_node((src.as_str(), *q)));
            }
            for (((srca, srcq), dsta), dstq) in app
                .job_graph_src_name
                .iter()
                .zip(app.job_graph_src_instance.iter())
                .zip(app.job_graph_dst_name.iter())
                .zip(app.job_graph_dst_instance.iter())
            {
                let srcf = *jobs_to_idx.get(&(srca.as_str(), *srcq)).expect("Should have all jobs added. Impossible error.");
                let dstf = *jobs_to_idx.get(&(dsta.as_str(), *dstq)).expect("Should have all jobs added. Impossible error.");
                for b in &app.buffers {
                    if app
                        .process_put_in_buffer_in_bits
                        .get(srca)
                        .and_then(|x| x.get(b))
                        .is_some()
                    {
                        if app
                            .process_get_from_buffer_in_bits
                            .get(dsta)
                            .and_then(|x| x.get(b))
                            .is_some()
                        {
                            let cur_idx = *messages_max_idx.get(b.as_str()).unwrap_or(&1);
                            let midx = full_graph.add_node((b.as_str(), cur_idx));
                            full_graph.add_edge(
                                srcf,
                                midx,
                                *actor_times.get(srca.as_str()).unwrap_or(&0.0),
                            );
                            full_graph.add_edge(midx, dstf, *buffer_times.get(b.as_str()).unwrap_or(&0.0));
                            messages_max_idx.insert(b.as_str(), cur_idx + 1);
                        }
                    }
                }
            }
        }
        let mut job_idx: HashMap<&str, u64> = self.aperiodic_asynchronous_dataflows.iter().flat_map(|app| app.processes.iter()).map(|a| (a.as_str(), 1)).collect();
        for (sched, list) in &self.super_loop_schedules {
            if !list.is_empty() {
                let mut job_list = vec![];
                for f in list {
                    let cur = *job_idx.get(f.as_str()).unwrap_or(&1);
                    job_list.push((f.as_str(), cur));
                    job_idx.insert(f.as_str(), cur + 1);
                }
                let pe = self.partitioned_tiled_multicore.runtimes.runtime_host.get(sched).unwrap();
                for i in 0..list.len()-1 {
                    let cur = job_list[i];
                    let next = job_list[i+1];
                    let srcf = *jobs_to_idx.get(&cur).expect("Should have all jobs added. Impossible error.");
                    let dstf = *jobs_to_idx.get(&next).expect("Should have all jobs added. Impossible error.");
                    full_graph.add_edge(
                        srcf,
                        dstf,
                        self.instrumented_computation_times
                            .average_execution_times
                            .get(list[i].as_str())
                            .and_then(|x| x.get(pe))
                            .map(|x| {
                                *x as f64
                                    / self.instrumented_computation_times.scale_factor as f64
                            })
                            .unwrap_or(0.0),
                    );
                    // do the same for the messages
                    let sending_messages: Vec<NodeIndex> = full_graph.neighbors_directed(srcf, Outgoing)
                        .filter(|x| full_graph.node_weight(*x).map(|(n, _)| !all_actors.contains(*n)).unwrap_or(false)).collect();
                    let later_messages: Vec<NodeIndex> = full_graph.neighbors_directed(dstf, Outgoing)
                        .filter(|x| full_graph.node_weight(*x).map(|(n, _)| !all_actors.contains(*n)).unwrap_or(false)).collect();
                    let timings: Vec<f64> = sending_messages.iter().map(|v| full_graph.edges_directed(*v, Outgoing).map(|e| *e.weight()).reduce(f64::max).unwrap_or(0.0)).collect();
                    for ((src, dst), time) in sending_messages.iter().zip(later_messages.iter()).zip(timings.iter()) {
                        full_graph.add_edge(*src, *dst, *time);
                    }
                }
                let srcf = *jobs_to_idx.get(&job_list[job_list.len()-1]).expect("Should have all jobs added. Impossible error.");
                let dstf = *jobs_to_idx.get(&job_list[0]).expect("Should have all jobs added. Impossible error.");
                full_graph.add_edge(
                    srcf,
                    dstf,
                    self.instrumented_computation_times
                        .average_execution_times
                        .get(list[list.len()-1].as_str())
                        .and_then(|x| x.get(pe))
                        .map(|x| {
                            *x as f64
                                / self.instrumented_computation_times.scale_factor as f64
                        })
                        .expect(format!("No computation time for source {} in {}", list[list.len()-1].as_str(), pe).as_str()),
                );
                let sending_messages: Vec<NodeIndex> = full_graph.neighbors_directed(srcf, Outgoing)
                    .filter(|x| full_graph.node_weight(*x).map(|(n, _)| !all_actors.contains(*n)).unwrap_or(false)).collect();
                let later_messages: Vec<NodeIndex> = full_graph.neighbors_directed(dstf, Outgoing)
                    .filter(|x| full_graph.node_weight(*x).map(|(n, _)| !all_actors.contains(*n)).unwrap_or(false)).collect();
                let timings: Vec<f64> = sending_messages.iter().map(|v| full_graph.edges_directed(*v, Outgoing).map(|e| *e.weight()).reduce(f64::max).unwrap_or(0.0)).collect();
                for ((src, dst), time) in sending_messages.iter().zip(later_messages.iter()).zip(timings.iter()) {
                    full_graph.add_edge(*src, *dst, *time);
                }
            }
        }
        let mut inv_throughput: HashMap<&str, f64> = self.aperiodic_asynchronous_dataflows.iter().flat_map(|app| 
            app.processes.iter().map(|a| (a.as_str(), 
                *actor_times.get(a.as_str()).unwrap_or(&0.0)
                + app.buffers.iter().filter(|b| app.process_put_in_buffer_in_bits.get(a).map(|x| x.contains_key(*b)).unwrap_or(false)).map(|b| *buffer_times.get(b.as_str()).unwrap_or(&0.0)).sum::<f64>()
            ))
        ).collect();
        let sccs = tarjan_scc(&full_graph);
        for scc in sccs {
            let scc_graph = full_graph.filter_map(
                |idx, w| {
                    if scc.contains(&idx) {
                        Some(*w)
                    } else {
                        None
                    }
                },
                |_, w| Some(*w),
            );
            let start = scc_graph.node_indices().next().unwrap();
            let (a, _) = scc_graph.node_weight(start).unwrap();
            let mut max_paths: HashMap<usize, f64> = HashMap::new();
            let mut bfs = Bfs::new(&scc_graph, start);
            // skip the initial one
            bfs.next(&scc_graph);
            while let Some(idx) = bfs.next(&scc_graph) {
                for incoming in scc_graph.edges_directed(idx, Incoming) {
                    let path_val = max_paths.get(&incoming.source().index()).unwrap_or(&0.0) + *incoming.weight();
                    max_paths.insert(idx.index(), path_val.max(*max_paths.get(&idx.index()).unwrap_or(&0.0)));
                }
            }
            for incoming in scc_graph.edges_directed(start, Incoming) {
                let path_val = max_paths.get(&incoming.source().index()).unwrap_or(&0.0) + *incoming.weight();
                let maxq = *scc_graph.node_weights().filter(|(aa, _)| a == aa).map(|(_, q)| q).max().unwrap_or(&1u64);
                let invth = path_val / maxq as f64;
                inv_throughput.insert(
                    a,
                    inv_throughput.get(a).unwrap_or(&0.0).max(invth),
                );
            }
            for i in scc_graph.node_indices().skip(1) {
                let (othera, otherq) = scc_graph.node_weight(i).unwrap();
                let invth = inv_throughput.get(othera).unwrap_or(&0.0).max(*inv_throughput.get(*a).unwrap_or(&0.0) * (*otherq as f64));
                inv_throughput.insert(
                    othera,
                    invth,
                );
            }
        }
        for app in &self.aperiodic_asynchronous_dataflows {
            for srca in &app.processes {
                for buf in &app.buffers {
                    let invth = inv_throughput.get(srca.as_str()).unwrap_or(&0.0).max(*inv_throughput.get(buf.as_str()).unwrap_or(&0.0));
                    inv_throughput.insert(
                        srca,
                        inv_throughput.get(srca.as_str()).unwrap_or(&0.0).max(invth),
                    );
                    inv_throughput.insert(
                        buf,
                        inv_throughput.get(buf.as_str()).unwrap_or(&0.0).max(invth),
                    );
                }
            }
            for srca in &app.processes {
                for dsta in &app.processes {
                    let invth = inv_throughput.get(srca.as_str()).unwrap_or(&0.0).max(*inv_throughput.get(dsta.as_str()).unwrap_or(&0.0));
                    inv_throughput.insert(
                        srca,
                        inv_throughput.get(srca.as_str()).unwrap_or(&0.0).max(invth),
                    );
                    inv_throughput.insert(
                        dsta,
                        inv_throughput.get(dsta.as_str()).unwrap_or(&0.0).max(invth),
                    );
                }
            }
        }
        inv_throughput.iter().filter(|(a, _)| self.aperiodic_asynchronous_dataflows.iter().any(|app| app.processes.contains(**a))).map(|(a, q)| (a.to_string(), *q)).collect()
    }
}

impl_decision_model_conversion!(AperiodicAsynchronousDataflowToPartitionedTiledMulticore);
impl DecisionModel for AperiodicAsynchronousDataflowToPartitionedTiledMulticore {
    impl_decision_model_standard_parts!(AperiodicAsynchronousDataflowToPartitionedTiledMulticore);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        for app in &self.aperiodic_asynchronous_dataflows {
            elems.extend(app.part().iter().map(|x| x.to_owned()));
        }
        elems.extend(
            self.partitioned_tiled_multicore
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_computation_times
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        for (pe, sched) in &self.processes_to_runtime_scheduling {
            elems.insert(format!("{}={}:{}-{}:{}", "scheduling", pe, "", sched, ""));
        }
        for (pe, mem) in &self.processes_to_memory_mapping {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", pe, "", mem, ""));
        }
        for (buf, mem) in &self.buffer_to_memory_mappings {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", buf, "", mem, ""));
        }
        for (pe, ce_slots) in &self.processing_elements_to_routers_reservations {
            for (ce, slots) in ce_slots {
                if *slots > 0 {
                    elems.insert(format!("{}={}:{}-{}:{}", "reservation", pe, "", ce, ""));
                }
            }
        }
        elems
    }
}

/// A decision model that combines aperiodic dataflows to partitioned memory mappable platforms.
///
/// The assumptions of this decision model are:
///  1. For every process, there is at least one processing element in the platform that can run it.
///     Otherwise, even the trivial mapping is impossible.
///  2. Super loop schedules are self-timed and stall the processing element that is hosting them.
///     That is, if we have a poor schedule, the processing element will get "blocked" often.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore {
    pub aperiodic_asynchronous_dataflows: Vec<AperiodicAsynchronousDataflow>,
    pub partitioned_mem_mappable_multicore: PartitionedMemoryMappableMulticore,
    pub instrumented_computation_times: InstrumentedComputationTimes,
    pub instrumented_memory_requirements: InstrumentedMemoryRequirements,
    pub processes_to_runtime_scheduling: HashMap<String, String>,
    pub processes_to_memory_mapping: HashMap<String, String>,
    pub buffer_to_memory_mappings: HashMap<String, String>,
    pub super_loop_schedules: HashMap<String, Vec<String>>,
    pub processing_elements_to_routers_reservations: HashMap<String, HashMap<String, u16>>,
}

impl_decision_model_conversion!(AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore);
impl DecisionModel for AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore {
    impl_decision_model_standard_parts!(
        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
    );

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        for app in &self.aperiodic_asynchronous_dataflows {
            elems.extend(app.part().iter().map(|x| x.to_owned()));
        }
        elems.extend(
            self.partitioned_mem_mappable_multicore
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_computation_times
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_memory_requirements
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        for (pe, sched) in &self.processes_to_runtime_scheduling {
            elems.insert(format!("{}={}:{}-{}:{}", "scheduling", pe, "", sched, ""));
        }
        for (pe, mem) in &self.processes_to_memory_mapping {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", pe, "", mem, ""));
        }
        for (buf, mem) in &self.buffer_to_memory_mappings {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", buf, "", mem, ""));
        }
        for (pe, ce_slots) in &self.processing_elements_to_routers_reservations {
            for (ce, slots) in ce_slots {
                if *slots > 0 {
                    elems.insert(format!("{}={}:{}-{}:{}", "reservation", pe, "", ce, ""));
                }
            }
        }
        elems
    }
}

/// A decision model that combines aperiodic dataflows to partitioned memory mappable platforms with
/// both software and hardware processing elements.
///
/// The assumptions of this decision model are:
///  1. For every process, there is at least one processing element in the platform that can run it.
///     Otherwise, even the trivial mapping is impossible.
///  2. Super loop schedules are self-timed and stall the processing element that is hosting them.
///     That is, if we have a poor schedule, the processing element will get "blocked" often.
#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL {
    pub aperiodic_asynchronous_dataflows: Vec<AperiodicAsynchronousDataflow>,
    pub partitioned_mem_mappable_multicore_and_pl: PartitionedMemoryMappableMulticoreAndPL,
    pub instrumented_computation_times: InstrumentedComputationTimes,
    pub instrumented_memory_requirements: InstrumentedMemoryRequirements,
    pub hardware_implementation_area: HardwareImplementationArea,
    pub processes_to_runtime_scheduling: HashMap<String, String>,
    pub processes_to_logic_programmable_areas: HashMap<String, String>,
    pub processes_to_memory_mapping: HashMap<String, String>,
    pub buffer_to_memory_mappings: HashMap<String, String>,
    pub super_loop_schedules: HashMap<String, Vec<String>>,
    pub processing_elements_to_routers_reservations: HashMap<String, HashMap<String, u16>>,
}

impl_decision_model_conversion!(
    AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL
);
impl DecisionModel for AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL {
    impl_decision_model_standard_parts!(
        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL
    );

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        for app in &self.aperiodic_asynchronous_dataflows {
            elems.extend(app.part().iter().map(|x| x.to_owned()));
        }
        elems.extend(
            self.partitioned_mem_mappable_multicore_and_pl
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_computation_times
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_memory_requirements
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.hardware_implementation_area
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        for (pe, sched) in &self.processes_to_runtime_scheduling {
            elems.insert(format!("{}={}:{}-{}:{}", "scheduling", pe, "", sched, ""));
        }
        for (pe, mem) in &self.processes_to_memory_mapping {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", pe, "", mem, ""));
        }
        for (buf, mem) in &self.buffer_to_memory_mappings {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", buf, "", mem, ""));
        }
        for (pe, ce_slots) in &self.processing_elements_to_routers_reservations {
            for (ce, slots) in ce_slots {
                if *slots > 0 {
                    elems.insert(format!("{}={}:{}-{}:{}", "reservation", pe, "", ce, ""));
                }
            }
        }
        elems
    }
}

impl AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL {
    pub fn get_max_discrete_value(&self) -> u64 {
        let biggest_path: u64 = self
            .partitioned_mem_mappable_multicore_and_pl
            .hardware
            .pre_computed_paths
            .values()
            .flat_map(|dsts| dsts.values().map(|path| path.len() as u64))
            .max()
            .unwrap_or(0)
            + 1;
        let number_jobs: u64 = self
            .aperiodic_asynchronous_dataflows
            .iter()
            .map(|app| app.processes.len() as u64)
            .sum();
        let num_mappables: u64 = self
            .partitioned_mem_mappable_multicore_and_pl
            .hardware
            .processing_elems
            .len() as u64
            + (self
                .partitioned_mem_mappable_multicore_and_pl
                .hardware
                .programmable_logic_elems
                .len() as u64);
        let relative_constant = (-(NUMERICAL_RELATIVE_ERROR.log2().ceil())) as u32;
        let problem_constant = (biggest_path * number_jobs * num_mappables) as f64;
        let resolution = (problem_constant.log2().ceil() as u32) + relative_constant;
        2u64.pow(resolution)
    }

    pub fn get_max_average_execution_time(&self) -> f32 {
        let original_max_plas = self
            .hardware_implementation_area
            .latencies_numerators
            .iter()
            .flat_map(|(proc, nums)| {
                nums.iter().flat_map(|(pla, num)| {
                    self.hardware_implementation_area
                        .latencies_denominators
                        .get(proc)
                        .and_then(|x| x.get(pla))
                        .map(|den| *num as f32 / *den as f32)
                })
            })
            .reduce(f32::max)
            .unwrap_or(0.0);
        let original_max_pes = self
            .instrumented_computation_times
            .average_execution_times
            .iter()
            .flat_map(|(_, times)| {
                times
                    .values()
                    .map(|x| *x as f32 / self.instrumented_computation_times.scale_factor as f32)
            })
            .reduce(f32::max)
            .unwrap_or(0.0);
        let original_max_traversal = self
            .partitioned_mem_mappable_multicore_and_pl
            .hardware
            .communication_elements_bit_per_sec_per_channel
            .values()
            .map(|x| 1.0 / x)
            .reduce(f64::max)
            .unwrap_or(0.0) as f32;
        original_max_pes
            .max(original_max_plas)
            .max(original_max_traversal)
    }

    pub fn get_memory_scale_factor(&self) -> u64 {
        *self
            .instrumented_memory_requirements
            .memory_requirements
            .values()
            .flat_map(|x| x.values())
            .chain(
                self.partitioned_mem_mappable_multicore_and_pl
                    .hardware
                    .storage_sizes
                    .values(),
            )
            .chain(self.aperiodic_asynchronous_dataflows.iter().flat_map(
                |app: &AperiodicAsynchronousDataflow| app.buffer_max_size_in_bits.values(),
            ))
            .chain(self.aperiodic_asynchronous_dataflows.iter().flat_map(
                |app: &AperiodicAsynchronousDataflow| {
                    app.process_get_from_buffer_in_bits
                        .values()
                        .flat_map(|x| x.values())
                },
            ))
            .chain(self.aperiodic_asynchronous_dataflows.iter().flat_map(
                |app: &AperiodicAsynchronousDataflow| {
                    app.process_get_from_buffer_in_bits
                        .values()
                        .flat_map(|x| x.values())
                },
            ))
            .filter(|x| x > &&0)
            .min()
            .unwrap_or(&1)
    }

    pub fn get_requirements_scale_factors(&self) -> HashMap<String, u64> {
        let programmable_resources_set: Vec<String> = self
            .hardware_implementation_area
            .provided_resources
            .values()
            .flat_map(|x| x.keys())
            .map(|x| x.to_string())
            .collect();
        let mut scale_factors = HashMap::new();
        for req in programmable_resources_set {
            let factor = *self
                .hardware_implementation_area
                .provided_resources
                .values()
                .flat_map(|x| x.values())
                .chain(
                    self.hardware_implementation_area
                        .required_resources
                        .values()
                        .flat_map(|x| x.values())
                        .flat_map(|x| x.values()),
                )
                .filter(|x| x > &&0)
                .min()
                .unwrap_or(&1);
            scale_factors.insert(req, factor);
        }
        scale_factors
    }
}

#[derive(Debug, PartialEq, Serialize, Deserialize, Clone, JsonSchema)]
pub struct PeriodicWorkloadToPartitionedSharedMultiCore {
    pub workload: CommunicatingAndTriggeredReactiveWorkload,
    pub platform: PartitionedMemoryMappableMulticore,
    pub instrumented_computation_times: InstrumentedComputationTimes,
    pub instrumented_memory_requirements: InstrumentedMemoryRequirements,
    pub process_mapping: Vec<(String, String)>,
    pub process_schedulings: Vec<(String, String)>,
    pub channel_mappings: Vec<(String, String)>,
    pub channel_slot_allocations: HashMap<String, HashMap<String, Vec<bool>>>,
    pub max_utilizations: HashMap<String, f64>,
}

impl_decision_model_conversion!(PeriodicWorkloadToPartitionedSharedMultiCore);
impl DecisionModel for PeriodicWorkloadToPartitionedSharedMultiCore {
    impl_decision_model_standard_parts!(PeriodicWorkloadToPartitionedSharedMultiCore);

    fn part(&self) -> HashSet<String> {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.workload.part().iter().map(|x| x.to_owned()));
        elems.extend(self.platform.part().iter().map(|x| x.to_owned()));
        elems.extend(
            self.instrumented_computation_times
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_memory_requirements
                .part()
                .iter()
                .map(|x| x.to_owned()),
        );
        for (pe, sched) in &self.process_schedulings {
            elems.insert(format!("{}={}:{}-{}:{}", "scheduling", pe, "", sched, ""));
        }
        for (pe, mem) in &self.process_mapping {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", pe, "", mem, ""));
        }
        for (buf, mem) in &self.channel_mappings {
            elems.insert(format!("{}={}:{}-{}:{}", "mapping", buf, "", mem, ""));
        }
        elems
    }
}
