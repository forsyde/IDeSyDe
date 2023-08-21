use idesyde_blueprints::{
    decision_message_to_model_gen, opaque_to_model_gen, StandaloneIdentificationModule,
};
use idesyde_core::decision_models_schemas_gen;
use models::{
    AnalysedSDFApplication, AperiodicAsynchronousDataflow,
    AperiodicAsynchronousDataflowToPartitionedTiledMulticore, InstrumentedComputationTimes,
    PartitionedTiledMulticore, RuntimesAndProcessors, SDFApplication, TiledMultiCore,
};
use schemars::schema_for;
use std::collections::HashSet;

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
        opaque_to_model_gen!(
            SDFApplication,
            AnalysedSDFApplication,
            TiledMultiCore,
            RuntimesAndProcessors,
            PartitionedTiledMulticore,
            AperiodicAsynchronousDataflow,
            InstrumentedComputationTimes,
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore
        ),
        decision_message_to_model_gen!(
            SDFApplication,
            AnalysedSDFApplication,
            TiledMultiCore,
            RuntimesAndProcessors,
            PartitionedTiledMulticore,
            AperiodicAsynchronousDataflow,
            InstrumentedComputationTimes,
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore
        ),
        decision_models_schemas_gen!(
            SDFApplication,
            AnalysedSDFApplication,
            TiledMultiCore,
            RuntimesAndProcessors,
            PartitionedTiledMulticore,
            AperiodicAsynchronousDataflow,
            InstrumentedComputationTimes,
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore
        ),
    )
}
