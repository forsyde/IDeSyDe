use std::{
    collections::HashSet,
    path::{Path, PathBuf},
};

use clap::Parser;
use idesyde_core::{
    headers::{load_decision_model_headers_from_binary, DecisionModelHeader, DesignModelHeader},
    write_design_model_header_to_path, DecisionModel, DesignModel, IdentificationModule,
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
    design_path_opt: Option<PathBuf>,
    #[arg(
        short = 'i',
        long,
        help = "The path where identified decision models (and headers) are stored."
    )]
    identified_path_opt: Option<PathBuf>,
    #[arg(
        short = 's',
        long,
        help = "The path where explored decision models (and headers) are stored."
    )]
    solved_path_opt: Option<PathBuf>,
    #[arg(
        short = 'r',
        long,
        help = "The path where integrated design models (and headers) are stored."
    )]
    reverse_path_opt: Option<PathBuf>,
    #[arg(
        short = 'o',
        long,
        help = "The path where final integrated design models are stored, in their original format."
    )]
    output_path_opt: Option<PathBuf>,
    #[arg(short = 't', help = "The overall identification iteration number.")]
    identification_step: i32,
}

pub trait StandaloneIdentificationModule {
    fn uid(&self) -> String;
    fn read_design_model(&self, path: &Path) -> Vec<Box<dyn DesignModel>>;
    fn write_design_model(&self, design_model: &Box<dyn DesignModel>, dest: &Path) -> PathBuf;
    fn decision_header_to_model(
        &self,
        header: &DecisionModelHeader,
    ) -> Option<Box<dyn DecisionModel>>;
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
        let mut identified: Vec<Box<dyn DecisionModel>> = Vec::new();
        for irule in self.identification_rules() {
            let f_opt = match irule {
                MarkedIdentificationRule::DesignModelOnlyIdentificationRule(f) => {
                    if iteration <= 0 {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(f) => {
                    if iteration > 0 {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::GenericIdentificationRule(f) => Some(f),
                MarkedIdentificationRule::SpecificDecisionModelIdentificationRule(ms, f) => {
                    let categories: HashSet<String> =
                        identified.iter().map(|x| x.header().category).collect();
                    if ms.iter().all(|x| categories.contains(x)) {
                        Some(f)
                    } else {
                        None
                    }
                }
            };
            if let Some(f) = f_opt {
                for m in f(design_models, decision_models) {
                    if !identified.contains(&m) {
                        identified.push(m);
                    }
                }
            }
        }
        identified
    }

    fn reverse_identification(
        &self,
        solved_decision_model: &Vec<Box<dyn DecisionModel>>,
        design_model: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>> {
        let mut reverse_identified: Vec<Box<dyn DesignModel>> = Vec::new();
        for f in self.reverse_identification_rules() {
            for m in f(solved_decision_model, design_model) {
                reverse_identified.push(m);
            }
        }
        reverse_identified
    }
}

fn execute_standalone_identification_module<
    T: StandaloneIdentificationModule + IdentificationModule,
>(
    module: T,
) {
    let args = IdentificationModuleArgs::parse();
    if let Some(design_path) = args.design_path_opt {
        std::fs::create_dir_all(&design_path)
            .expect("Failed to create the design path during reverse identification.");
        let design_models = module.read_design_model(&design_path);
        for m in &design_models {
            write_design_model_header_to_path(m, &design_path, "header", &module.uid());
        }
        match (
            args.identified_path_opt,
            args.solved_path_opt,
            args.reverse_path_opt,
        ) {
            (_, Some(solved_path), Some(reverse_path)) => {
                std::fs::create_dir_all(&solved_path)
                    .expect("Failed to create the solved path during reverse identification.");
                std::fs::create_dir_all(&reverse_path)
                    .expect("Failed to create the reverse path during reverse identification.");
                let solved: Vec<Box<dyn DecisionModel>> =
                    load_decision_model_headers_from_binary(&solved_path)
                        .iter()
                        .flat_map(|(p, x)| module.decision_header_to_model(x))
                        .collect();
                let reverse_identified = module.reverse_identification(&solved, &design_models);
                for m in reverse_identified {
                    let mut h = m.header();
                    if let Some(p) = module.write_design_model(&m, &reverse_path).to_str() {
                        h.model_paths.push(p.to_string());
                    };
                    // write_design_model_header_to_path(m, p, prefix_str, suffix_str)
                }
            }
            (Some(identified_path), None, None) => (),
            _ => (),
        }
    }
}
