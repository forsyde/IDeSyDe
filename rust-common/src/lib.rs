use idesyde_core::{decision_models_schemas_gen, RustEmbeddedModule};
use models::{
    AnalysedSDFApplication, AperiodicAsynchronousDataflow,
    AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore,
    AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL,
    AperiodicAsynchronousDataflowToPartitionedTiledMulticore, InstrumentedComputationTimes,
    InstrumentedMemoryRequirements, MM_MCoreAndPL, MemoryMappableMultiCore,
    PartitionedMemoryMappableMulticore, PartitionedMemoryMappableMulticoreAndPL,
    PartitionedTiledMulticore, RuntimesAndProcessors, SDFApplication, TiledMultiCore,
};
use schemars::schema_for;
use std::{collections::HashSet, sync::Arc};

pub mod irules;
pub mod models;

pub fn make_module() -> RustEmbeddedModule {
    RustEmbeddedModule::builder()
        .unique_identifier("CommonRustModule".to_string())
        .identification_rules(vec![
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_partitioned_tiled_multicore,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_asynchronous_aperiodic_dataflow_from_sdf,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_aperiodic_asynchronous_dataflow_to_partitioned_tiled_multicore,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_partitioned_mem_mapped_multicore,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_partitioned_mem_mapped_multicore_and_pl,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_aperiodic_asynchronous_dataflow_to_partitioned_mem_mappable_multicore_and_pl,
        )),
        Arc::new(idesyde_core::MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(
            irules::identify_analyzed_sdf_from_common_sdf,
        ))
    ])
        // .opaque_to_model(opaque_to_model_gen![
        //     SDFApplication,
        //     AnalysedSDFApplication,
        //     TiledMultiCore,
        //     RuntimesAndProcessors,
        //     PartitionedTiledMulticore,
        //     AperiodicAsynchronousDataflow,
        //     InstrumentedComputationTimes,
        //     InstrumentedMemoryRequirements,
        //     AperiodicAsynchronousDataflowToPartitionedTiledMulticore,
        //     MemoryMappableMultiCore,
        //     PartitionedMemoryMappableMulticore,
        //     AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
        // ])
        .decision_model_json_schemas(decision_models_schemas_gen![
            SDFApplication,
            AnalysedSDFApplication,
            TiledMultiCore,
            RuntimesAndProcessors,
            PartitionedTiledMulticore,
            AperiodicAsynchronousDataflow,
            InstrumentedComputationTimes,
            InstrumentedMemoryRequirements,
            AperiodicAsynchronousDataflowToPartitionedTiledMulticore,
            MemoryMappableMultiCore,
            MM_MCoreAndPL,
            PartitionedMemoryMappableMulticore,
            PartitionedMemoryMappableMulticoreAndPL,
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore,
            AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL
        ])
        .build()
        .expect("Failed to build common standalone identification module. Should never happen.")
}
