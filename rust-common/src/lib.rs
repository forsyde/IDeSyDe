use idesyde_core::{
    decision_header_to_model_gen, decision_models_schemas_gen, load_decision_model, DecisionModel,
    StandaloneIdentificationModule,
};
use schemars::schema_for;
use std::collections::HashSet;
use std::sync::Arc;

pub mod irules;
pub mod models;

pub fn make_common_module() -> StandaloneIdentificationModule {
    StandaloneIdentificationModule::without_design_models(
        "CommonIdentificationModule",
        vec![
            idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
                irules::identify_partitioned_tiled_multicore,
            ),
            idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
                irules::identify_asynchronous_aperiodic_dataflow_from_sdf,
            ),
            idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
                irules::identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore,
            ),
        ],
        Vec::new(),
        decision_header_to_model_gen!(
            models::SDFApplication,
            models::AnalysedSDFApplication,
            models::TiledMultiCore,
            models::RuntimesAndProcessors,
            models::PartitionedTiledMulticore,
            models::AperiodicAsynchronousDataflow,
            models::InstrumentedComputationTimes,
            models::AperiodicAsynchronousDataflowToPartitionedTiledMulticore
        ),
        decision_models_schemas_gen!(
            models::SDFApplication,
            models::AnalysedSDFApplication,
            models::TiledMultiCore,
            models::RuntimesAndProcessors,
            models::PartitionedTiledMulticore,
            models::AperiodicAsynchronousDataflow,
            models::InstrumentedComputationTimes,
            models::AperiodicAsynchronousDataflowToPartitionedTiledMulticore
        ),
    )
}
