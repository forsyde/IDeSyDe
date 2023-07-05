use std::collections::HashSet;

use idesyde_blueprints::execute_standalone_identification_module;
use idesyde_core::{
    decision_header_to_model_gen, decision_models_schemas_gen, load_decision_model, DecisionModel,
    StandaloneIdentificationModule,
};
use schemars::schema_for;

fn main() {
    let common_module = StandaloneIdentificationModule::without_design_models(
        "CommonIdentificationModule",
        vec![
            idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
                idesyde_common::irules::identify_partitioned_tiled_multicore,
            ),
            idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
                idesyde_common::irules::identify_asynchronous_aperiodic_dataflow_from_sdf,
            ),
        ],
        Vec::new(),
        decision_header_to_model_gen!(
            idesyde_common::models::SDFApplication,
            idesyde_common::models::AnalysedSDFApplication,
            idesyde_common::models::TiledMultiCore,
            idesyde_common::models::RuntimesAndProcessors,
            idesyde_common::models::PartitionedTiledMulticore,
            idesyde_common::models::AsynchronousAperiodicDataflow
        ),
        decision_models_schemas_gen!(
            idesyde_common::models::SDFApplication,
            idesyde_common::models::AnalysedSDFApplication,
            idesyde_common::models::TiledMultiCore,
            idesyde_common::models::RuntimesAndProcessors,
            idesyde_common::models::PartitionedTiledMulticore,
            idesyde_common::models::AsynchronousAperiodicDataflow
        ),
    );
    execute_standalone_identification_module(common_module);
}
