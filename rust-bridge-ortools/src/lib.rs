use std::collections::HashMap;

use idesyde_common::models::PeriodicWorkloadToPartitionedSharedMultiCore;
use idesyde_core::{
    cast_dyn_decision_model, ExplorationBid, Explorer, Module, OpaqueDecisionModel,
};

#[cxx::bridge]
mod ffi {

    struct WorkloadDSEInput<'a> {
        pe_targets: &'a [u32],
        process_me_targets: &'a [u32],
        buffer_me_targets: &'a [u32],
        buffer_ce_targets: &'a [u32],
        memory_limits: &'a [u64],
        process_sizes: &'a [u64],
        buffer_sizes: &'a [u64],
        ce_max_slots: &'a [u32],
        wcets: &'a [&'a [u32]],
        pe_and_me_paths: &'a [&'a [&'a [u32]]],
    }

    unsafe extern "C++" {
        include!("rust-bridge-ortools/include/solutions.hh");

        fn prepare_workload_dse_model(input: &WorkloadDSEInput);

    }
}

struct ORToolExplorer;

impl Explorer for ORToolExplorer {
    fn unique_identifier(&self) -> String {
        "ORToolExplorer".to_string()
    }

    fn location_url(&self) -> Option<Url> {
        None
    }

    fn bid(
        &self,
        _other_explorers: &Vec<std::sync::Arc<dyn Explorer>>,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
    ) -> idesyde_core::ExplorationBid {
        if let Some(m) = cast_dyn_decision_model!(m, PeriodicWorkloadToPartitionedSharedMultiCore) {
            ExplorationBid {
                can_explore: true,
                is_exact: true,
                competitiveness: 1.0,
                target_objectives: "nUsedPEs",
                additional_numeric_properties: HashMap::new(),
            }
        }
        idesyde_core::ExplorationBid::impossible(self.unique_identifier().as_str())
    }

    fn explore(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
        _currrent_solutions: &std::collections::HashSet<idesyde_core::ExplorationSolution>,
        _exploration_configuration: idesyde_core::ExplorationConfiguration,
    ) -> Box<dyn Iterator<Item = idesyde_core::ExplorationSolution> + Send + Sync + '_> {
        if let Some(m) = cast_dyn_decision_model!(m, PeriodicWorkloadToPartitionedSharedMultiCore) {
        }
        Box::new(std::iter::empty())
    }
}

struct ORToolsModule;

impl Module for ORToolsModule {
    fn unique_identifier(&self) -> String {
        "ORToolsModule".to_string()
    }

    fn explorers(&self) -> Vec<std::sync::Arc<dyn Explorer>> {
        vec![Arc::new(ORToolExplorer)]
    }
}
