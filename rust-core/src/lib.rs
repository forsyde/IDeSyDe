pub mod headers;

use std::{
    collections::{HashMap, HashSet},
    fs,
    hash::Hash,
    path::Path,
};

use headers::{DecisionModelHeader, DesignModelHeader, ExplorationBid};
use serde::Serialize;
use std::cmp::Ordering;

pub trait DesignModel {
    fn unique_identifier(&self) -> String;

    fn header(&self) -> DesignModelHeader;
}

pub trait DecisionModel {
    fn unique_identifier(&self) -> String;

    fn header(&self) -> DecisionModelHeader;

    fn body_as_json(&self) -> Option<String> {
        None
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        None
    }

    fn dominates(&self, o: Box<dyn DecisionModel>) -> bool {
        match self.header().partial_cmp(&o.header()) {
            Some(Ordering::Greater) => true,
            _ => false,
        }
    }
}

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

// pub fn write_design_model_to_path<M: DesignModel + ?Sized>(
//     m: &Box<M>,
//     p: &Path,
//     prefix_str: &str,
//     suffix_str: &str,
// ) -> DesignModelHeader {
//     let h = m.header();
//     write_design_model_header_to_path(&h, p, prefix_str, suffix_str);
//     DesignModelHeader {
//         category: h.category,
//         model_paths: Vec::new(),
//         elements: h.elements,
//         relations: h.relations,
//     }
// }

pub fn write_design_model_header_to_path(
    h: &DesignModelHeader,
    p: &Path,
    prefix_str: &str,
    suffix_str: &str,
) -> bool {
    let jstr = serde_json::to_string(&h).expect("Failed to serialize decision model to json.");
    std::fs::write(
        p.join(format!(
            "header_{}_{}_{}.json",
            prefix_str, h.category, suffix_str
        )),
        jstr,
    )
    .expect("Failed to write serialized decision model during identification.");
    let msg = rmp_serde::to_vec(&h).expect("Failed to serialize decision model to msgpack.");
    let target_path = p.join(format!(
        "header_{}_{}_{}.msgpack",
        prefix_str, h.category, suffix_str
    ));
    fs::write(&target_path, msg)
        .expect("Failed to write serialized dominant model during identification.");
    true
}

pub fn write_decision_model_header_to_path(
    h: &DecisionModelHeader,
    p: &Path,
    prefix_str: &str,
    suffix_str: &str,
) -> bool {
    let jstr = serde_json::to_string(h).expect("Failed to serialize decision model to json.");
    std::fs::write(
        p.join(format!(
            "body_{}_{}_{}.json",
            prefix_str, h.category, suffix_str
        )),
        jstr,
    )
    .expect("Failed to write serialized decision model during identification.");
    let msg = rmp_serde::to_vec(h).expect("Failed to serialize decision model to msgpack.");
    let target_path = p.join(format!(
        "body_{}_{}_{}.msgpack",
        prefix_str, h.category, suffix_str
    ));
    fs::write(&target_path, msg)
        .expect("Failed to write serialized dominant model during identification.");
    true
}

pub fn write_decision_model_to_path<M: DecisionModel + ?Sized>(
    m: &Box<M>,
    p: &Path,
    prefix_str: &str,
    suffix_str: &str,
) -> DecisionModelHeader {
    let mut h = m.header();
    if let Some(j) = m.body_as_json() {
        let p = format!("body_{}_{}_{}.json", prefix_str, h.category, suffix_str);
        std::fs::write(&p, j).expect("Failed to write JSON body of decision model.");
        h.body_path = Some(p);
    }
    if let Some(b) = m.body_as_msgpack() {
        let p = format!("body_{}_{}_{}.msgpack", prefix_str, h.category, suffix_str);
        std::fs::write(&p, b).expect("Failed to write MsgPack body of decision model.");
        h.body_path = Some(p);
    }
    write_decision_model_header_to_path(&h, p, prefix_str, suffix_str);
    h
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

pub trait IdentificationModule {
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

pub trait ExplorationModule {
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

pub trait StandaloneIdentificationModule {
    fn uid(&self) -> String;
    fn read_design_model(&self, path: &Path) -> Option<Box<dyn DesignModel>>;
    fn write_design_model(&self, design_model: &Box<dyn DesignModel>, dest: &Path) -> bool;
    fn decision_header_to_model(
        &self,
        header: &DecisionModelHeader,
    ) -> Option<Box<dyn DecisionModel>>;
    fn identification_rules(&self) -> Vec<MarkedIdentificationRule>;
    fn reverse_identification_rules(&self) -> Vec<ReverseIdentificationRule>;
}

impl<T: StandaloneIdentificationModule> IdentificationModule for T {
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
