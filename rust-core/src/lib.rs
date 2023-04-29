pub mod headers;

use std::{collections::HashMap, fs, hash::Hash, path::Path};

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

pub fn write_decision_model_header_to_path<M: DecisionModel + ?Sized>(
    m: &Box<M>,
    p: &Path,
    prefix_str: &str,
    suffix_str: &str,
) -> DecisionModelHeader {
    let h = m.header();
    let jstr = serde_json::to_string(&h).expect("Failed to serialize decision model to json.");
    std::fs::write(
        p.join(format!(
            "header_{}_{}_{}.json",
            prefix_str,
            m.unique_identifier(),
            suffix_str
        )),
        jstr,
    )
    .expect("Failed to write serialized decision model during identification.");
    let msg = rmp_serde::to_vec(&h).expect("Failed to serialize decision model to msgpack.");
    let target_path = p.join(format!(
        "header_{}_{}_{}.msgpack",
        prefix_str,
        m.unique_identifier(),
        suffix_str
    ));
    fs::write(&target_path, msg)
        .expect("Failed to write serialized dominant model during identification.");
    DecisionModelHeader {
        category: h.category,
        body_path: Some(target_path.to_str().unwrap().to_string()),
        covered_elements: h.covered_elements,
        covered_relations: h.covered_relations,
    }
}

pub fn write_design_model_header_to_path<M: DesignModel + ?Sized>(
    m: &Box<M>,
    p: &Path,
    prefix_str: &str,
    suffix_str: &str,
) -> DesignModelHeader {
    let h = m.header();
    let jstr = serde_json::to_string(&h).expect("Failed to serialize decision model to json.");
    std::fs::write(
        p.join(format!(
            "header_{}_{}_{}.json",
            prefix_str,
            m.unique_identifier(),
            suffix_str
        )),
        jstr,
    )
    .expect("Failed to write serialized decision model during identification.");
    let msg = rmp_serde::to_vec(&h).expect("Failed to serialize decision model to msgpack.");
    let target_path = p.join(format!(
        "header_{}_{}_{}.msgpack",
        prefix_str,
        m.unique_identifier(),
        suffix_str
    ));
    fs::write(&target_path, msg)
        .expect("Failed to write serialized dominant model during identification.");
    DesignModelHeader {
        category: h.category,
        model_paths: Vec::new(),
        elements: h.elements,
        relations: h.relations,
    }
}

pub fn write_decision_model_to_path<M: DecisionModel + Serialize + ?Sized>(
    m: &Box<M>,
    p: &Path,
    prefix_str: &str,
    suffix_str: &str,
) -> DecisionModelHeader {
    let h = write_decision_model_header_to_path(m, p, prefix_str, suffix_str);
    let jstr = serde_json::to_string(m).expect("Failed to serialize decision model to json.");
    std::fs::write(
        p.join(format!(
            "body_{}_{}_{}.json",
            prefix_str,
            m.unique_identifier(),
            suffix_str
        )),
        jstr,
    )
    .expect("Failed to write serialized decision model during identification.");
    let msg = rmp_serde::to_vec(m).expect("Failed to serialize decision model to msgpack.");
    let target_path = p.join(format!(
        "body_{}_{}_{}.msgpack",
        prefix_str,
        m.unique_identifier(),
        suffix_str
    ));
    fs::write(&target_path, msg)
        .expect("Failed to write serialized dominant model during identification.");
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
