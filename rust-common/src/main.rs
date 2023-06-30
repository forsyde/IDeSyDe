use std::collections::HashSet;

use idesyde_blueprints::execute_standalone_identification_module;
use idesyde_common::identify_partitioned_tiled_multicore;
use idesyde_core::{
    decision_header_to_model_gen, decision_models_schemas_gen, load_decision_model, DecisionModel,
    StandaloneIdentificationModule,
};
use schemars::schema_for;

fn main() {
    let common_module = StandaloneIdentificationModule::without_design_models(
        "CommonIdentificationModule",
        vec![
            idesyde_core::MarkedIdentificationRule::DesignModelOnlyIdentificationRule(
                identify_partitioned_tiled_multicore,
            ),
        ],
        Vec::new(),
        decision_header_to_model_gen!(
            idesyde_common::SDFApplication,
            idesyde_common::TiledMultiCore,
            idesyde_common::RuntimesAndProcessors,
            idesyde_common::PartitionedTiledMulticore
        ),
        decision_models_schemas_gen!(
            idesyde_common::SDFApplication,
            idesyde_common::TiledMultiCore,
            idesyde_common::RuntimesAndProcessors,
            idesyde_common::PartitionedTiledMulticore
        ),
    );
    execute_standalone_identification_module(common_module);
}
