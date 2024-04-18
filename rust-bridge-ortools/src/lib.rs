use std::{
    collections::{HashMap, HashSet},
    sync::{Arc, Mutex},
};

use idesyde_common::models::PeriodicWorkloadToPartitionedSharedMultiCore;
use idesyde_core::{
    cast_dyn_decision_model, ExplorationBid, ExplorationSolution, Explorer, Module,
    OpaqueDecisionModel,
};

use autocxx::prelude::*; // use all the main autocxx functions

include_cpp! {
    #include "ortools/base/logging.h"
    #include "ortools/sat/cp_model.h"
    #include "ortools/sat/cp_model.pb.h"
    #include "ortools/sat/cp_model_solver.h"
    #include "ortools/util/sorted_interval_list.h"
    safety!(unsafe) // see details of unsafety policies described in the 'safety' section of the book
    // generate!("CpModelBuilder") // add this line for each function or type you wish to generate
}

struct ORToolExplorer;

impl Explorer for ORToolExplorer {
    fn unique_identifier(&self) -> String {
        "ORToolExplorer".to_string()
    }

    fn bid(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
    ) -> idesyde_core::ExplorationBid {
        if let Ok(_) = PeriodicWorkloadToPartitionedSharedMultiCore::try_from(m.as_ref()) {
            let mut objs = HashSet::new();
            objs.insert("nUsedPEs".to_string());
            return ExplorationBid {
                can_explore: true,
                is_exact: true,
                competitiveness: 1.0,
                target_objectives: objs,
                additional_numeric_properties: HashMap::new(),
            };
        }
        ExplorationBid::impossible()
    }

    fn explore(
        &self,
        m: std::sync::Arc<dyn idesyde_core::DecisionModel>,
        _currrent_solutions: &std::collections::HashSet<idesyde_core::ExplorationSolution>,
        _exploration_configuration: idesyde_core::ExplorationConfiguration,
    ) -> Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>> {
        if let Ok(_) = PeriodicWorkloadToPartitionedSharedMultiCore::try_from(m.as_ref()) {}
        Arc::new(Mutex::new(std::iter::empty()))
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
