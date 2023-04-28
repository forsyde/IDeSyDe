use std::path::{Path, PathBuf};

use clap::Parser;
use idesyde_core::{
    DecisionModel, DecisionModelHeader, DesignModel, IdentificationModule,
    MarkedIdentificationRule, ReverseIdentificationRule,
};

#[derive(Parser, Debug)]
#[command(author = "Rodolfo Jordao")]
pub struct IdentificationModuleArgs {
    #[arg(
        short = 'm',
        long,
        help = "The path where the design models (and headers) are stored."
    )]
    design_path: Option<PathBuf>,
    #[arg(
        short = 'i',
        long,
        help = "The path where identified decision models (and headers) are stored."
    )]
    identified_path: Option<PathBuf>,
    #[arg(
        short = 's',
        long,
        help = "The path where explored decision models (and headers) are stored."
    )]
    solved_path: Option<PathBuf>,
    #[arg(
        short = 'r',
        long,
        help = "The path where integrated design models (and headers) are stored."
    )]
    integration_path: Option<PathBuf>,
    #[arg(
        short = 'o',
        long,
        help = "The path where final integrated design models are stored, in their original format."
    )]
    output_path: Option<PathBuf>,
    #[arg(short = 't', help = "The overall identification iteration number.")]
    identification_step: i32,
}

pub trait StandaloneIdentificationModule {
    fn uid(&self) -> String;
    fn read_design_model(&self, path: &Path) -> Vec<Box<dyn DesignModel>>;
    fn write_design_model(&self, design_model: &Box<dyn DesignModel>) -> Path;
    fn decision_header_to_model(&self, header: &DecisionModelHeader) -> Box<dyn DecisionModel>;
    fn identification_rules(&self) -> Vec<MarkedIdentificationRule>;
    fn reverse_identification_rules(&self) -> Vec<ReverseIdentificationRule>;
}

impl IdentificationModule for &dyn StandaloneIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.uid()
    }

    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Box<dyn DecisionModel>>,
    ) -> Vec<Box<dyn DecisionModel>> {
        todo!()
    }

    fn reverse_identification(
        &self,
        design_model: &Box<dyn DesignModel>,
        decision_model: &Box<dyn DecisionModel>,
    ) -> Vec<Box<dyn DesignModel>> {
        todo!()
    }
}
