pub mod macros;

use std::{
    collections::{HashMap, HashSet},
    path::{Path, PathBuf},
    sync::Arc,
};

use clap::Parser;
use idesyde_core::{
    headers::{load_decision_model_headers_from_binary, DecisionModelHeader, ExplorationBid},
    DecisionModel, DesignModel, ExplorationModule, ExplorationSolution, Explorer,
    IdentificationModule, MarkedIdentificationRule, ReverseIdentificationRule,
};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Clone, Deserialize, Debug, PartialEq, Eq)]
pub struct DecisionModelMessage {
    header: DecisionModelHeader,
    body: Option<String>,
}

impl DecisionModelMessage {
    pub fn from_dyn_decision_model(m: &dyn DecisionModel) -> DecisionModelMessage {
        DecisionModelMessage {
            header: m.header(),
            body: m
                .body_as_json()
                .map(|x| x.replace("\r\n", "\\r\\n").replace("\n", "\\n")),
        }
    }

    pub fn from_decision_model<T: DecisionModel>(m: &T) -> DecisionModelMessage {
        DecisionModelMessage {
            header: m.header(),
            body: m
                .body_as_json()
                .map(|x| x.replace("\r\n", "\\r\\n").replace("\n", "\\n")),
        }
    }

    pub fn from_json_str(s: &str) -> Option<DecisionModelMessage> {
        match serde_json::from_str(s) {
            Ok(m) => Some(m),
            Err(e) => {
                println!("{}", e);
                None
            }
        }
    }

    pub fn header(&self) -> DecisionModelHeader {
        self.header.to_owned()
    }

    pub fn body_with_newlines_unescaped(&self) -> Option<String> {
        self.body
            .to_owned()
            .map(|x| x.replace("\\r\\n", "\r\n").replace("\\n", "\n"))
    }

    pub fn to_json_str(&self) -> String {
        serde_json::to_string(self)
            .expect("Failed to serialize DecisionModelMessage, which should always suceed.")
    }
}

#[derive(Clone, PartialEq, Serialize, Deserialize)]
pub struct ExplorationSolutionMessage {
    pub objectives: HashMap<String, f64>,
    pub solved: DecisionModelMessage,
}

impl ExplorationSolutionMessage {
    pub fn from_json_str(s: &str) -> Option<ExplorationSolutionMessage> {
        serde_json::from_str(s).ok()
    }
}

pub struct StandaloneExplorationModule {
    unique_identifier: String,
    explorers: Vec<Box<dyn Explorer>>,
}

impl ExplorationModule for StandaloneExplorationModule {
    fn unique_identifier(&self) -> String {
        self.unique_identifier.to_owned()
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<ExplorationBid> {
        self.explorers.iter().map(|e| e.bid(m.clone())).collect()
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>> {
        self.explorers
            .iter()
            .find(|e| e.unique_identifier() == explorer_id)
            .map(|e| {
                e.explore(
                    m,
                    max_sols,
                    total_timeout,
                    time_resolution,
                    memory_resolution,
                )
            })
            .unwrap_or(Box::new(std::iter::empty::<Arc<dyn DecisionModel>>()))
    }

    fn iter_explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        currrent_solutions: Vec<idesyde_core::ExplorationSolution>,
        exploration_configuration: idesyde_core::ExplorationConfiguration,
        solution_iter: fn(ExplorationSolution) -> (),
    ) -> Vec<idesyde_core::ExplorationSolution> {
        self.explorers
            .iter()
            .find(|e| e.unique_identifier() == explorer_id)
            .map(|e| {
                e.iter_explore(
                    m,
                    currrent_solutions,
                    solution_iter,
                    exploration_configuration,
                )
            })
            .unwrap_or(vec![])
    }
}

pub struct StandaloneIdentificationModule {
    unique_identifier: String,
    identification_rules: Vec<MarkedIdentificationRule>,
    reverse_identification_rules: Vec<ReverseIdentificationRule>,
    read_design_model: fn(path: &Path) -> Option<Box<dyn DesignModel>>,
    write_design_model: fn(design_model: &Box<dyn DesignModel>, dest: &Path) -> Vec<PathBuf>,
    decision_header_to_model: fn(header: &DecisionModelHeader) -> Option<Arc<dyn DecisionModel>>,
    decision_message_to_model: fn(header: &DecisionModelMessage) -> Option<Arc<dyn DecisionModel>>,
    pub decision_model_schemas: HashSet<String>,
}

impl StandaloneIdentificationModule {
    pub fn minimal(unique_identifier: &str) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules: vec![],
            reverse_identification_rules: vec![],
            read_design_model: |_x| None,
            write_design_model: |_x, _p| vec![],
            decision_header_to_model: |_x| None,
            decision_message_to_model: |_x| None,
            decision_model_schemas: HashSet::new(),
        };
    }

    pub fn without_design_models(
        unique_identifier: &str,
        identification_rules: Vec<MarkedIdentificationRule>,
        reverse_identification_rules: Vec<ReverseIdentificationRule>,
        decision_header_to_model: fn(
            header: &DecisionModelHeader,
        ) -> Option<Arc<dyn DecisionModel>>,
        decision_message_to_model: fn(
            header: &DecisionModelMessage,
        ) -> Option<Arc<dyn DecisionModel>>,
        decision_model_schemas: HashSet<String>,
    ) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules,
            reverse_identification_rules,
            read_design_model: |_x| None,
            write_design_model: |_x, _p| vec![],
            decision_header_to_model,
            decision_message_to_model,
            decision_model_schemas,
        };
    }

    pub fn complete(
        unique_identifier: &str,
        identification_rules: Vec<MarkedIdentificationRule>,
        reverse_identification_rules: Vec<ReverseIdentificationRule>,
        read_design_model: fn(path: &Path) -> Option<Box<dyn DesignModel>>,
        write_design_model: fn(design_model: &Box<dyn DesignModel>, dest: &Path) -> Vec<PathBuf>,
        decision_header_to_model: fn(
            header: &DecisionModelHeader,
        ) -> Option<Arc<dyn DecisionModel>>,
        decision_message_to_model: fn(
            header: &DecisionModelMessage,
        ) -> Option<Arc<dyn DecisionModel>>,
        decision_model_schemas: HashSet<String>,
    ) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules,
            reverse_identification_rules,
            read_design_model,
            write_design_model,
            decision_header_to_model,
            decision_message_to_model,
            decision_model_schemas,
        };
    }

    pub fn read_design_model(&self, path: &Path) -> Option<Box<dyn DesignModel>> {
        return (self.read_design_model)(path);
    }
    pub fn write_design_model(
        &self,
        design_model: &Box<dyn DesignModel>,
        dest: &Path,
    ) -> Vec<PathBuf> {
        return (self.write_design_model)(design_model, dest);
    }
    pub fn decision_header_to_model(
        &self,
        header: &DecisionModelHeader,
    ) -> Option<Arc<dyn DecisionModel>> {
        return (self.decision_header_to_model)(header);
    }
}

impl IdentificationModule for StandaloneIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.unique_identifier.to_owned()
    }

    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Vec<Arc<dyn DecisionModel>> {
        let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
        for irule in &self.identification_rules {
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
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>> {
        let mut reverse_identified: Vec<Box<dyn DesignModel>> = Vec::new();
        for f in &self.reverse_identification_rules {
            for m in f(solved_decision_models, design_models) {
                reverse_identified.push(m);
            }
        }
        reverse_identified
    }
}

#[derive(Parser, Debug)]
#[command(author = "Rodolfo Jordao")]
pub struct IdentificationModuleArgs {
    #[arg(
        short = 'm',
        long = "design-path",
        help = "The path where the design models (and headers) are stored."
    )]
    design_path_opt: Option<PathBuf>,
    #[arg(
        short = 'i',
        long = "identified-path",
        help = "The path where identified decision models (and headers) are stored."
    )]
    identified_path_opt: Option<PathBuf>,
    #[arg(
        short = 's',
        long = "solved-path",
        help = "The path where explored decision models (and headers) are stored."
    )]
    solved_path_opt: Option<PathBuf>,
    #[arg(
        short = 'r',
        long = "reverse-path",
        help = "The path where integrated design models (and headers) are stored."
    )]
    reverse_path_opt: Option<PathBuf>,
    #[arg(
        short = 'o',
        long = "output-path",
        help = "The path where final integrated design models are stored, in their original format."
    )]
    output_path_opt: Option<PathBuf>,
    #[arg(short = 't', help = "The overall identification iteration number.")]
    identification_step: Option<i32>,
    #[arg(
        long = "schemas",
        help = "Prints decision model schemas from this module.",
        default_value = "false"
    )]
    print_schema: bool,
}

pub fn execute_standalone_identification_module(module: StandaloneIdentificationModule) {
    match IdentificationModuleArgs::try_parse() {
        Ok(args) => {
            if args.print_schema {
                for schema in &module.decision_model_schemas {
                    println!("{}", schema);
                }
            } else {
                if let Some(design_path) = args.design_path_opt {
                    std::fs::create_dir_all(&design_path)
                        .expect("Failed to create the design path during reverse identification.");
                    let mut design_models: Vec<Box<dyn DesignModel>> = Vec::new();
                    for pres in std::fs::read_dir(&design_path)
                        .expect("Failed to read design path during start-up.")
                    {
                        let p = pres.expect("Failed to read directory entry during start-up");
                        if let Some(m) = module.read_design_model(&p.path()) {
                            let mut h = m.header();
                            h.model_paths.push(
                                p.path()
                                    .to_str()
                                    .expect("Failed to get OS string during start-up")
                                    .to_string(),
                            );
                            h.write_to_dir(&design_path, "", &module.unique_identifier());
                            design_models.push(m);
                        }
                    }
                    match (
                        args.identified_path_opt,
                        args.solved_path_opt,
                        args.reverse_path_opt,
                        args.identification_step,
                    ) {
                        (_, Some(solved_path), Some(reverse_path), _) => {
                            std::fs::create_dir_all(&solved_path).expect(
                                "Failed to create the solved path during reverse identification.",
                            );
                            std::fs::create_dir_all(&reverse_path).expect(
                                "Failed to create the reverse path during reverse identification.",
                            );
                            let solved: Vec<Arc<dyn DecisionModel>> =
                                load_decision_model_headers_from_binary(&solved_path)
                                    .iter()
                                    .flat_map(|(_, x)| module.decision_header_to_model(x))
                                    .collect();
                            let reverse_identified =
                                module.reverse_identification(&solved, &design_models);
                            for m in reverse_identified {
                                for rpath in module.write_design_model(&m, &reverse_path) {
                                    let mut h = m.header();
                                    h.model_paths.push(rpath.to_str().expect("Failed to get a string out of the output path during reverse identification").to_string());
                                    h.write_to_dir(
                                        &reverse_path,
                                        "",
                                        module.unique_identifier().as_str(),
                                    );
                                }
                                if let Some(out_path) = &args.output_path_opt {
                                    module.write_design_model(&m, out_path);
                                }
                            }
                        }
                        (Some(identified_path), None, None, Some(ident_step)) => {
                            std::fs::create_dir_all(&identified_path).expect(
                                "Failed to create the identified path during reverse identification.",
                            );
                            let decision_models: Vec<Arc<dyn DecisionModel>> =
                                load_decision_model_headers_from_binary(&identified_path)
                                    .iter()
                                    .flat_map(|(_, x)| module.decision_header_to_model(x))
                                    .collect();
                            let identified = module.identification_step(
                                ident_step,
                                &design_models,
                                &decision_models,
                            );
                            for m in identified {
                                m.write_to_dir(
                                    &identified_path,
                                    format!("{:0>16}", ident_step).as_str(),
                                    module.unique_identifier().as_str(),
                                );
                            }
                        }
                        _ => (),
                    }
                }
            }
        }
        Err(_) => {
            println!("Incorrect combination of parameters/options. Check usage with -h/--help.");
            std::process::exit(64);
        }
    }
}
