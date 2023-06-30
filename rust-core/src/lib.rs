pub mod headers;

use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    path::{Path, PathBuf},
};

use downcast_rs::{impl_downcast, DowncastSync};
use headers::{DecisionModelHeader, DesignModelHeader, ExplorationBid};
use serde::de::DeserializeOwned;
use std::cmp::Ordering;

/// The trait/interface for a design model in the design space identification methodology, as
/// defined in [1].
///
/// A design model is a model used by MDE frameworks and tools, e.g. Simulink and ForSyDe IO.
/// Like [DesignModel], this trait requires a header so that the identification procedure can work
/// correctly and terminate. The header gives an idea to the framework on how much can be "identified"
/// from the input MDE model, i.e. the [DesignModel].
///
/// [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
///
pub trait DesignModel: Send + DowncastSync {
    fn unique_identifier(&self) -> String;

    fn header(&self) -> DesignModelHeader;
}
impl_downcast!(sync DesignModel);

/// The trait/interface for a decision model in the design space identification methodology, as
/// defined in [1].
///
/// A decision model is a collection of parameters and associated functions that potentially define design spaces,
///  e.g. a decision model for SDFs with a topology matrix parameter and an associated function to check the existence of deadlocks.
///
/// The header is a necessary abstraction to ensure that the identification procedure terminates properly.
/// It also gives and idea on how much of the input models are being "covered" by the decision model in question.
///
/// [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
///
pub trait DecisionModel: Send + DowncastSync {
    fn unique_identifier(&self) -> String;

    fn header(&self) -> DecisionModelHeader;

    fn body_as_json(&self) -> Option<String> {
        None
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        None
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        None
    }

    fn dominates(&self, o: Box<dyn DecisionModel>) -> bool {
        match self.header().partial_cmp(&o.header()) {
            Some(Ordering::Greater) => true,
            _ => false,
        }
    }

    fn write_to_dir(&self, p: &Path, prefix_str: &str, suffix_str: &str) -> DecisionModelHeader {
        let mut h = self.header();
        if let Some(j) = self.body_as_json() {
            let p = format!("body_{}_{}_{}.json", prefix_str, h.category, suffix_str);
            std::fs::write(&p, j).expect("Failed to write JSON body of decision model.");
            h.body_path = Some(p);
        }
        if let Some(b) = self.body_as_msgpack() {
            let p = format!("body_{}_{}_{}.msgpack", prefix_str, h.category, suffix_str);
            std::fs::write(&p, b).expect("Failed to write MsgPack body of decision model.");
            h.body_path = Some(p);
        }
        if let Some(b) = self.body_as_cbor() {
            let p = format!("body_{}_{}_{}.cbor", prefix_str, h.category, suffix_str);
            std::fs::write(&p, b).expect("Failed to write CBOR body of decision model.");
            h.body_path = Some(p);
        }
        h.write_to_dir(p, prefix_str, suffix_str);
        h
    }
}
impl_downcast!(sync DecisionModel);

impl DecisionModel for DecisionModelHeader {
    fn unique_identifier(&self) -> String {
        self.category.to_owned()
    }

    fn header(&self) -> DecisionModelHeader {
        self.to_owned()
    }
}

impl PartialEq<dyn DecisionModel> for dyn DecisionModel {
    fn eq(&self, other: &dyn DecisionModel) -> bool {
        self.unique_identifier() == other.unique_identifier() && self.header() == other.header()
    }
}

impl PartialOrd<dyn DecisionModel> for dyn DecisionModel {
    fn partial_cmp(&self, other: &dyn DecisionModel) -> Option<Ordering> {
        self.header().partial_cmp(&other.header())
    }
}

pub type IdentificationRule =
    fn(&Vec<Box<dyn DesignModel>>, &Vec<Box<dyn DecisionModel>>) -> Vec<Box<dyn DecisionModel>>;

pub type ReverseIdentificationRule =
    fn(&Vec<Box<dyn DecisionModel>>, &Vec<Box<dyn DesignModel>>) -> Vec<Box<dyn DesignModel>>;

pub enum MarkedIdentificationRule {
    DesignModelOnlyIdentificationRule(IdentificationRule),
    DecisionModelOnlyIdentificationRule(IdentificationRule),
    SpecificDecisionModelIdentificationRule(Vec<String>, IdentificationRule),
    GenericIdentificationRule(IdentificationRule),
}

pub trait IdentificationModule: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Box<dyn DecisionModel>>,
    ) -> Vec<Box<dyn DecisionModel>>;
    fn reverse_identification(
        &self,
        decision_model: &Vec<Box<dyn DecisionModel>>,
        design_model: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>>;
}

impl PartialEq<dyn IdentificationModule> for dyn IdentificationModule {
    fn eq(&self, other: &dyn IdentificationModule) -> bool {
        self.unique_identifier() == other.unique_identifier()
    }
}

impl Eq for dyn IdentificationModule {}

impl Hash for dyn IdentificationModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.unique_identifier().hash(state);
    }
}

pub trait ExplorationModule: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn available_criterias(&self, m: Box<dyn DecisionModel>) -> HashMap<String, f32>;
    fn bid(&self, m: &Box<dyn DecisionModel>) -> ExplorationBid;
    fn explore(
        &self,
        m: &Box<dyn DecisionModel>,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Box<dyn Iterator<Item = Box<dyn DecisionModel>>>;
}

impl PartialEq<dyn ExplorationModule> for dyn ExplorationModule {
    fn eq(&self, other: &dyn ExplorationModule) -> bool {
        self.unique_identifier() == other.unique_identifier()
    }
}

impl Eq for dyn ExplorationModule {}

pub struct StandaloneIdentificationModule {
    unique_identifier: String,
    identification_rules: Vec<MarkedIdentificationRule>,
    reverse_identification_rules: Vec<ReverseIdentificationRule>,
    read_design_model: fn(path: &Path) -> Option<Box<dyn DesignModel>>,
    write_design_model: fn(design_model: &Box<dyn DesignModel>, dest: &Path) -> Vec<PathBuf>,
    decision_header_to_model: fn(header: &DecisionModelHeader) -> Option<Box<dyn DecisionModel>>,
    pub decision_model_schemas: HashSet<String>,
}

impl StandaloneIdentificationModule {
    pub fn without_design_model(
        unique_identifier: &str,
        identification_rules: Vec<MarkedIdentificationRule>,
        reverse_identification_rules: Vec<ReverseIdentificationRule>,
        decision_header_to_model: fn(
            header: &DecisionModelHeader,
        ) -> Option<Box<dyn DecisionModel>>,
        decision_model_schemas: HashSet<String>,
    ) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules,
            reverse_identification_rules,
            read_design_model: |_x| None,
            write_design_model: |_x, _p| vec![],
            decision_header_to_model,
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
        ) -> Option<Box<dyn DecisionModel>>,
        decision_model_schemas: HashSet<String>,
    ) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules,
            reverse_identification_rules,
            read_design_model,
            write_design_model,
            decision_header_to_model,
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
    ) -> Option<Box<dyn DecisionModel>> {
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
        decision_models: &Vec<Box<dyn DecisionModel>>,
    ) -> Vec<Box<dyn DecisionModel>> {
        let mut identified: Vec<Box<dyn DecisionModel>> = Vec::new();
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
        solved_decision_models: &Vec<Box<dyn DecisionModel>>,
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

pub fn load_decision_model<T: DecisionModel + DeserializeOwned>(
    path: &std::path::PathBuf,
) -> Option<T> {
    if let Ok(f) = std::fs::File::open(path) {
        if let Some(ext) = path.extension() {
            if ext.eq_ignore_ascii_case("cbor") {
                return ciborium::from_reader(f).ok();
            } else if ext.eq_ignore_ascii_case("msgpack") {
                return rmp_serde::from_read(f).ok();
            } else if ext.eq_ignore_ascii_case("json") {
                return serde_json::from_reader(f).ok();
            }
        }
    }
    None
}
