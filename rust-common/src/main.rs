use idesyde_blueprints::execute_standalone_identification_module;
use idesyde_core::StandaloneIdentificationModule;

pub mod common;

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
        _header: &idesyde_core::headers::DecisionModelHeader,
    ) -> Option<Box<dyn idesyde_core::DecisionModel>> {
        todo!()
    }

    fn identification_rules(&self) -> Vec<idesyde_core::MarkedIdentificationRule> {
        todo!()
    }

    fn reverse_identification_rules(&self) -> Vec<idesyde_core::ReverseIdentificationRule> {
        todo!()
    }
}
fn main() {
    execute_standalone_identification_module(CommonIdentificationModule {});
}
