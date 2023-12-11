pub mod macros;

use std::{
    collections::{HashMap, HashSet},
    path::{Path, PathBuf},
    sync::Arc,
};

use clap::Parser;
use derive_builder::Builder;
use idesyde_core::{
    DecisionModel, DesignModel, ExplorationSolution, Explorer, IdentificationIterator,
    IdentificationResult, MarkedIdentificationRule, Module, OpaqueDecisionModel,
    ReverseIdentificationRule,
};
use log::debug;
use serde::{Deserialize, Serialize};

use base64::{engine::general_purpose, Engine as _};

use rayon::prelude::*;

#[derive(Clone, PartialEq, Serialize, Deserialize)]
pub struct ExplorationSolutionMessage {
    pub objectives: HashMap<String, f64>,
    pub solved: OpaqueDecisionModel,
}

impl ExplorationSolutionMessage {
    pub fn from_json_str(s: &str) -> Result<ExplorationSolutionMessage, serde_json::Error> {
        serde_json::from_str(s)
    }

    pub fn to_json_str(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }

    pub fn to_cbor<O>(&self) -> Result<O, ciborium::ser::Error<std::io::Error>>
    where
        O: From<Vec<u8>>,
    {
        let mut buf: Vec<u8> = Vec::new();
        ciborium::into_writer(self, buf.as_mut_slice())?;
        Ok(buf.into())
    }

    pub fn from_cbor<R>(b: R) -> Result<Self, ciborium::de::Error<std::io::Error>>
    where
        R: std::io::Read,
    {
        ciborium::from_reader(b)
    }
}

impl From<ExplorationSolution> for ExplorationSolutionMessage {
    fn from(value: ExplorationSolution) -> Self {
        ExplorationSolutionMessage {
            objectives: value.objectives,
            solved: OpaqueDecisionModel::from(value.solved),
        }
    }
}

impl From<&ExplorationSolution> for ExplorationSolutionMessage {
    fn from(value: &ExplorationSolution) -> Self {
        ExplorationSolutionMessage {
            objectives: value.objectives.to_owned(),
            solved: OpaqueDecisionModel::from(value.solved.as_ref()),
        }
    }
}

#[derive(Clone, PartialEq, Serialize, Deserialize)]
pub struct IdentificationResultMessage {
    pub identified: Vec<OpaqueDecisionModel>,
    pub messages: HashSet<String>,
}

impl TryFrom<&str> for IdentificationResultMessage {
    type Error = serde_json::Error;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        serde_json::from_str(value)
    }
}

#[derive(Clone, PartialEq, Serialize, Deserialize)]
pub struct IdentificationResultCompactMessage {
    pub identified: HashSet<String>,
    pub messages: HashSet<String>,
}

impl From<IdentificationResultMessage> for IdentificationResultCompactMessage {
    fn from(value: IdentificationResultMessage) -> Self {
        IdentificationResultCompactMessage {
            identified: value
                .identified
                .into_iter()
                .map(|x| x.global_md5_hash())
                .map(|hash| general_purpose::STANDARD_NO_PAD.encode(hash))
                .collect(),
            messages: value.messages,
        }
    }
}

impl From<&ExplorationSolutionMessage> for OpaqueDecisionModel {
    fn from(value: &ExplorationSolutionMessage) -> Self {
        OpaqueDecisionModel {
            body_json: value.solved.body_json.to_owned(),
            body_msgpack: value.solved.body_msgpack.to_owned(),
            body_cbor: value.solved.body_cbor.to_owned(),
            category: value.solved.category.to_owned(),
            part: value.solved.part.to_owned(),
        }
    }
}

#[derive(Clone, Builder, PartialEq, Eq)]
pub struct StandaloneModule {
    unique_identifier: String,
    #[builder(default = "Vec::new()")]
    explorers: Vec<Arc<dyn Explorer>>,
    #[builder(default = "vec![]")]
    identification_rules: Vec<MarkedIdentificationRule>,
    #[builder(default = "vec![]")]
    reverse_identification_rules: Vec<ReverseIdentificationRule>,
    #[builder(default = "|_| { None }")]
    read_design_model: fn(path: &Path) -> Option<Arc<dyn DesignModel>>,
    #[builder(default = "|_, _| { vec![] }")]
    write_design_model: fn(design_model: &Arc<dyn DesignModel>, dest: &Path) -> Vec<PathBuf>,
    #[builder(default = "|_| { None }")]
    opaque_to_model: fn(header: &OpaqueDecisionModel) -> Option<Arc<dyn DecisionModel>>,
    #[builder(default = "HashSet::new()")]
    pub decision_model_json_schemas: HashSet<String>,
}

impl StandaloneModule {
    pub fn read_design_model(&self, path: &Path) -> Option<Arc<dyn DesignModel>> {
        return (self.read_design_model)(path);
    }
    pub fn write_design_model(
        &self,
        design_model: &Arc<dyn DesignModel>,
        dest: &Path,
    ) -> Vec<PathBuf> {
        return (self.write_design_model)(design_model, dest);
    }
    pub fn opaque_to_model(&self, opaque: &OpaqueDecisionModel) -> Option<Arc<dyn DecisionModel>> {
        return (self.opaque_to_model)(opaque);
    }
}

/// A simple iterator for performing identification in-process.
#[derive(Builder, PartialEq, Eq, Clone)]
struct DefaultIdentificationIterator {
    #[builder(default = "Vec::new()")]
    design_models: Vec<Arc<dyn DesignModel>>,
    #[builder(default = "Vec::new()")]
    decision_models: Vec<Arc<dyn DecisionModel>>,
    imodule: Arc<StandaloneModule>,
}

impl Iterator for DefaultIdentificationIterator {
    type Item = IdentificationResult;

    fn next(&mut self) -> Option<Self::Item> {
        // let mut identified = vec![];
        // Assume that all the models which could have been made non-opaque, did.
        // let (tx_model, rx_model) = std::sync::mpsc::channel::<Arc<dyn DecisionModel>>();
        // let (tx_msg, rx_msg) = std::sync::mpsc::channel::<String>();
        let par_identified: Vec<IdentificationResult> = self
            .imodule
            .identification_rules
            .par_iter()
            .flat_map_iter(|irule| match irule {
                MarkedIdentificationRule::DesignModelOnlyIdentificationRule(f) => {
                    if !self.design_models.is_empty() {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(f) => {
                    if !self.decision_models.is_empty() {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::GenericIdentificationRule(f) => Some(f),
                MarkedIdentificationRule::SpecificDecisionModelIdentificationRule(ms, f) => {
                    if ms
                        .iter()
                        .all(|x| self.decision_models.iter().any(|y| x == &y.category()))
                    {
                        Some(f)
                    } else {
                        None
                    }
                }
            })
            .map(|f| f(&self.design_models, &self.decision_models))
            .map(|(models, msgs)| {
                (
                    models
                        .into_iter()
                        .map(|model| {
                            model
                                .downcast_ref::<OpaqueDecisionModel>()
                                .and_then(|opaque| self.imodule.opaque_to_model(opaque))
                                .unwrap_or(model)
                        })
                        .collect(),
                    msgs,
                )
            })
            .collect();
        let mut messages = vec![];
        for (ms, msgs) in par_identified {
            for m in ms {
                if !self.decision_models.contains(&m) {
                    self.decision_models.push(m);
                }
            }
            for msg in msgs {
                if !messages.contains(&msg) {
                    messages.push(msg);
                }
            }
        }
        Some((self.decision_models.clone(), messages))
    }
}

impl IdentificationIterator for DefaultIdentificationIterator {
    fn next_with_models(
        &mut self,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> Option<IdentificationResult> {
        // first, add everything
        for m in design_models {
            if !self.design_models.contains(m) {
                self.design_models.push(m.to_owned());
            }
        }
        for m in decision_models {
            let refined = m
                .downcast_ref::<OpaqueDecisionModel>()
                .and_then(|opaque| self.imodule.opaque_to_model(opaque))
                .unwrap_or(m.to_owned());
            if !self.decision_models.contains(&refined) {
                self.decision_models.push(refined.to_owned());
            }
        }
        return self.next();
    }

    // fn collect_messages(&mut self) -> Vec<(String, String)> {
    //     self.messages
    //         .iter()
    //         .map(|x| ("DEBUG".to_string(), x.to_owned()))
    //         .collect()
    // }
}

impl Module for StandaloneModule {
    fn unique_identifier(&self) -> String {
        self.unique_identifier.to_owned()
    }

    fn identification_step(
        &self,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> IdentificationResult {
        // Box::new(DefaultIdentificationIteratorBuilder::default()
        //     .decision_models(initial_decision_models.to_owned())
        //     .design_models(initial_design_models.to_owned())
        //     .imodule(Arc::new(self.to_owned()))
        //     .build()
        //     .expect("Failed to create an identification iterator by an identification module. Should never happen."))
        // Assume that all the models which could have been made non-opaque, did.
        // let (tx_model, rx_model) = std::sync::mpsc::channel::<Arc<dyn DecisionModel>>();
        // let (tx_msg, rx_msg) = std::sync::mpsc::channel::<String>();
        let mut identified_models = vec![];
        for m in decision_models {
            if let Some(refined) = m.downcast_ref::<OpaqueDecisionModel>().and_then(self.opaque_to_model) {
                // debug!("Refining a {}", refined.category());
                identified_models.push(refined);
            } else {
                identified_models.push(m.clone());
            }
        }
        let par_identified: Vec<IdentificationResult> = self
            .identification_rules
            .par_iter()
            .flat_map_iter(|irule| match irule {
                MarkedIdentificationRule::DesignModelOnlyIdentificationRule(f) => {
                    if !design_models.is_empty() {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(f) => {
                    if !decision_models.is_empty() {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::GenericIdentificationRule(f) => Some(f),
                MarkedIdentificationRule::SpecificDecisionModelIdentificationRule(ms, f) => {
                    if ms
                        .iter()
                        .all(|x| decision_models.iter().any(|y| x == &y.category()))
                    {
                        Some(f)
                    } else {
                        None
                    }
                }
            })
            .map(|f| f(&design_models, &identified_models))
            .map(|(models, msgs)| {
                (
                    models
                        .into_iter()
                        .map(|model| {
                            model
                                .downcast_ref::<OpaqueDecisionModel>()
                                .and_then(self.opaque_to_model)
                                .unwrap_or(model)
                        })
                        .collect(),
                    msgs,
                )
            })
            .collect();
        let mut messages = vec![];
        for (ms, msgs) in par_identified {
            for m in ms {
                if !identified_models.contains(&m) {
                    identified_models.push(m);
                }
            }
            for msg in msgs {
                if !messages.contains(&msg) {
                    messages.push(msg);
                }
            }
        }
        (identified_models, messages)
    }

    fn reverse_identification(
        &self,
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> Vec<Arc<dyn DesignModel>> {
        let decs = solved_decision_models.to_owned();
        let dess = design_models.to_owned();
        self.reverse_identification_rules
            .par_iter()
            .flat_map(move |f| f(&decs, &dess))
            .collect()
    }

    fn explorers(&self) -> Vec<Arc<dyn Explorer>> {
        self.explorers.to_owned()
    }
}

#[derive(Parser, Debug)]
#[command(author = "Rodolfo Jordao")]
pub struct ModuleArgs {
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

pub fn execute_standalone_module(module: StandaloneModule) {
    match ModuleArgs::try_parse() {
        Ok(args) => {
            if args.print_schema {
                for schema in &module.decision_model_json_schemas {
                    println!("{}", schema);
                }
            } else {
                if let Some(design_path) = args.design_path_opt {
                    std::fs::create_dir_all(&design_path)
                        .expect("Failed to create the design path during reverse identification.");
                    // let mut design_models: Vec<Arc<dyn DesignModel>> = Vec::new();
                    // for pres in std::fs::read_dir(&design_path)
                    //     .expect("Failed to read design path during start-up.")
                    // {
                    // let p = pres.expect("Failed to read directory entry during start-up");
                    // if let Some(m) = module.read_design_model(&p.path()) {
                    //     let mut h = m.header();
                    //     h.model_paths.push(
                    //         p.path()
                    //             .to_str()
                    //             .expect("Failed to get OS string during start-up")
                    //             .to_string(),
                    //     );
                    //     h.write_to_dir(&design_path, "", &module.unique_identifier());
                    //     design_models.push(m);
                    // }
                    // }
                    // match (
                    //     args.identified_path_opt,
                    //     args.solved_path_opt,
                    //     args.reverse_path_opt,
                    //     args.identification_step,
                    // ) {
                    //     (_, Some(solved_path), Some(reverse_path), _) => {
                    //         // std::fs::create_dir_all(&solved_path).expect(
                    //         //     "Failed to create the solved path during reverse identification.",
                    //         // );
                    //         // std::fs::create_dir_all(&reverse_path).expect(
                    //         //     "Failed to create the reverse path during reverse identification.",
                    //         // );
                    //         // let solved: Vec<Arc<dyn DecisionModel>> = todo!();
                    //         // // load_decision_model_headers_from_binary(&solved_path)
                    //         // //     .iter()
                    //         // //     .flat_map(|(_, x)| module.decision_header_to_model(x))
                    //         // //     .collect();
                    //         // let reverse_identified =
                    //         //     module.reverse_identification(&solved, &design_models);
                    //         // for m in reverse_identified {
                    //         //     for rpath in module.write_design_model(&m, &reverse_path) {
                    //         //         let mut h = m.header();
                    //         //         h.model_paths.push(rpath.to_str().expect("Failed to get a string out of the output path during reverse identification").to_string());
                    //         //         h.write_to_dir(
                    //         //             &reverse_path,
                    //         //             "",
                    //         //             module.unique_identifier().as_str(),
                    //         //         );
                    //         //     }
                    //         //     if let Some(out_path) = &args.output_path_opt {
                    //         //         module.write_design_model(&m, out_path);
                    //         //     }
                    //         // }
                    //     }
                    //     (Some(identified_path), None, None, Some(ident_step)) => {
                    //         // std::fs::create_dir_all(&identified_path).expect(
                    //         //     "Failed to create the identified path during reverse identification.",
                    //         // );
                    //         // let decision_models: Vec<Arc<dyn DecisionModel>> = todo!();
                    //         // // load_decision_model_headers_from_binary(&identified_path)
                    //         // //     .iter()
                    //         // //     .flat_map(|(_, x)| module.decision_header_to_model(x))
                    //         // //     .collect();
                    //         // let (identified, _) = module.identification_step(
                    //         //     ident_step,
                    //         //     &design_models,
                    //         //     &decision_models,
                    //         // );
                    //         // for m in identified {
                    //         //     m.write_to_dir(
                    //         //         &identified_path,
                    //         //         format!("{:0>16}", ident_step).as_str(),
                    //         //         module.unique_identifier().as_str(),
                    //         //     );
                    //         // }
                    //     }
                    //     _ => (),
                    // }
                }
            }
        }
        Err(_) => {
            println!("Incorrect combination of parameters/options. Check usage with -h/--help.");
            std::process::exit(64);
        }
    }
}
