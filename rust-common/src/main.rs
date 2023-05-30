use idesyde_blueprints::execute_standalone_identification_module;
use idesyde_common::{
    identify_partitioned_tiled_multicore, PartitionedTiledMulticore, RuntimesAndProcessors,
    SDFApplication, TiledMultiCore,
};
use idesyde_core::{load_decision_model, DecisionModel, StandaloneIdentificationModule};
use schemars::schema_for;

struct CommonIdentificationModule {}

impl StandaloneIdentificationModule for CommonIdentificationModule {
    fn uid(&self) -> String {
        "CommonIdentificationModule".to_owned()
    }

    fn read_design_model(
        &self,
        _path: &std::path::Path,
    ) -> Option<Box<dyn idesyde_core::DesignModel>> {
        None
    }

    fn write_design_model(
        &self,
        _design_model: &Box<dyn idesyde_core::DesignModel>,
        _dest: &std::path::Path,
    ) -> bool {
        false
    }

    fn decision_header_to_model(
        &self,
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
                "PartitionedTiledMulticore" => {
                    load_decision_model::<PartitionedTiledMulticore>(&bpath)
                        .map(|m| Box::new(m) as Box<dyn DecisionModel>)
                }
                _ => None,
            }
        })
    }

    fn identification_rules(&self) -> Vec<idesyde_core::MarkedIdentificationRule> {
        vec![
            idesyde_core::MarkedIdentificationRule::DesignModelOnlyIdentificationRule(
                identify_partitioned_tiled_multicore,
            ),
        ]
    }

    fn reverse_identification_rules(&self) -> Vec<idesyde_core::ReverseIdentificationRule> {
        Vec::new()
    }

    fn decision_models_schemas(&self) -> Vec<String> {
        vec![
            serde_json::to_string_pretty(&schema_for!(idesyde_common::SDFApplication)).unwrap(),
            serde_json::to_string_pretty(&schema_for!(idesyde_common::TiledMultiCore)).unwrap(),
            serde_json::to_string_pretty(&schema_for!(idesyde_common::RuntimesAndProcessors))
                .unwrap(),
            serde_json::to_string_pretty(&schema_for!(idesyde_common::PartitionedTiledMulticore))
                .unwrap(),
        ]
    }
}
fn main() {
    execute_standalone_identification_module(CommonIdentificationModule {});
}
