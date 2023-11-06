use std::collections::{HashMap, HashSet};

use idesyde_core::{
    headers::DecisionModelHeader, impl_decision_model_standard_parts, DecisionModel,
};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

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
    pub actors_identifiers: HashSet<String>,
    pub self_concurrent_actors: HashSet<String>,
    pub channels_identifiers: HashSet<String>,
    pub topology_srcs: Vec<String>,
    pub topology_dsts: Vec<String>,
    pub topology_production: Vec<u64>,
    pub topology_consumption: Vec<u64>,
    pub topology_initial_tokens: Vec<u64>,
    pub topology_token_size_in_bits: Vec<u64>,
    pub topology_channel_names: Vec<HashSet<String>>,
    pub actor_minimum_throughputs: HashMap<String, f64>,
    pub channel_token_sizes: HashMap<String, u64>,
    pub chain_maximum_latency: HashMap<String, HashMap<String, f64>>,
}

impl DecisionModel for SDFApplication {
    impl_decision_model_standard_parts!(SDFApplication);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.actors_identifiers.iter().map(|x| x.to_owned()));
        elems.extend(self.channels_identifiers.iter().map(|x| x.to_owned()));
        // for i in 0..self.topology_srcs.len() {
        //     elems.insert(format!(
        //         "({}, {}, {})={}:{}-{}:{}",
        //         self.topology_production[i],
        //         self.topology_production[i],
        //         self.topology_initial_token[i],
        //         self.topology_srcs[i],
        //         "",
        //         self.topology_dsts[i],
        //         ""
        //     ));
        // }
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
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

impl DecisionModel for AnalysedSDFApplication {
    impl_decision_model_standard_parts!(AnalysedSDFApplication);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        let sdfheader = self.sdf_application.header();
        elems.extend(sdfheader.covered_elements.iter().map(|x| x.to_owned()));
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

impl DecisionModel for MemoryMappableMultiCore {
    impl_decision_model_standard_parts!(MemoryMappableMultiCore);

    fn header(&self) -> DecisionModelHeader {
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
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
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

impl DecisionModel for PartitionedMemoryMappableMulticore {
    impl_decision_model_standard_parts!(PartitionedMemoryMappableMulticore);

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

impl DecisionModel for AperiodicAsynchronousDataflow {
    impl_decision_model_standard_parts!(AsynchronousAperiodicDataflow);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.buffers.iter().map(|x| x.to_string()));
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
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

impl DecisionModel for InstrumentedComputationTimes {
    impl_decision_model_standard_parts!(InstrumentedComputationTimes);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.processing_elements.iter().map(|x| x.to_string()));
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
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

impl DecisionModel for InstrumentedMemoryRequirements {
    impl_decision_model_standard_parts!(InstrumentedmemoryRequirements);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.processing_elements.iter().map(|x| x.to_string()));
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
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

impl DecisionModel for AperiodicAsynchronousDataflowToPartitionedTiledMulticore {
    impl_decision_model_standard_parts!(AperiodicAsynchronousDataflowToPartitionedTiledMulticore);

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        for app in &self.aperiodic_asynchronous_dataflows {
            elems.extend(app.header().covered_elements.iter().map(|x| x.to_owned()));
        }
        elems.extend(
            self.partitioned_tiled_multicore
                .header()
                .covered_elements
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_computation_times
                .header()
                .covered_elements
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
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
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

impl DecisionModel for AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore {
    impl_decision_model_standard_parts!(
        AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
    );

    fn header(&self) -> DecisionModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        for app in &self.aperiodic_asynchronous_dataflows {
            elems.extend(app.header().covered_elements.iter().map(|x| x.to_owned()));
        }
        elems.extend(
            self.partitioned_mem_mappable_multicore
                .header()
                .covered_elements
                .iter()
                .map(|x| x.to_owned()),
        );
        elems.extend(
            self.instrumented_computation_times
                .header()
                .covered_elements
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
        DecisionModelHeader {
            category: self.category(),
            body_path: None,
            covered_elements: elems.into_iter().collect(),
        }
    }
}
