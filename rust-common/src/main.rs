use std::collections::HashSet;

use idesyde_blueprints::execute_standalone_identification_module;
use idesyde_common::{
    identify_partitioned_tiled_multicore, PartitionedTiledMulticore, RuntimesAndProcessors,
    SDFApplication, TiledMultiCore,
};
use idesyde_core::{load_decision_model, DecisionModel, StandaloneIdentificationModule};
use schemars::schema_for;

fn decision_header_to_model(
    header: &idesyde_core::headers::DecisionModelHeader,
) -> Option<Box<dyn idesyde_core::DecisionModel>> {
    header.body_path.as_ref().and_then(|bp| {
        let bpath = std::path::PathBuf::from(bp);
        match header.category.as_str() {
            "SDFApplication" => load_decision_model::<SDFApplication>(&bpath)
                .map(|m| Box::new(m) as Box<dyn DecisionModel>),
            "TiledMultiCore" => load_decision_model::<TiledMultiCore>(&bpath)
                .map(|m| Box::new(m) as Box<dyn DecisionModel>),
            "RuntimesAndProcessors" => load_decision_model::<RuntimesAndProcessors>(&bpath)
                .map(|m| Box::new(m) as Box<dyn DecisionModel>),
            "PartitionedTiledMulticore" => load_decision_model::<PartitionedTiledMulticore>(&bpath)
                .map(|m| Box::new(m) as Box<dyn DecisionModel>),
            _ => None,
        }
    })
}
fn main() {
    let common_module = StandaloneIdentificationModule::new(
        "CommonIdentificationModule".to_owned(),
        vec![
            idesyde_core::MarkedIdentificationRule::DesignModelOnlyIdentificationRule(
                identify_partitioned_tiled_multicore,
            ),
        ],
        Vec::new(),
        |x| None,
        |x, p| false,
        decision_header_to_model,
        HashSet::from([
            serde_json::to_string_pretty(&schema_for!(idesyde_common::SDFApplication)).unwrap(),
            serde_json::to_string_pretty(&schema_for!(idesyde_common::TiledMultiCore)).unwrap(),
            serde_json::to_string_pretty(&schema_for!(idesyde_common::RuntimesAndProcessors))
                .unwrap(),
            serde_json::to_string_pretty(&schema_for!(idesyde_common::PartitionedTiledMulticore))
                .unwrap(),
        ]),
    );
    execute_standalone_identification_module(common_module);
}
