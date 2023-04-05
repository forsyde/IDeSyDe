use std::{collections::HashMap, fs, hash::Hash, path::Path};

use serde::{Deserialize, Serialize};
use std::cmp::Ordering;

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct LabelledArcWithPorts {
    src: String,
    src_port: Option<String>,
    label: Option<String>,
    dst: String,
    dst_port: Option<String>,
}

impl PartialEq<LabelledArcWithPorts> for LabelledArcWithPorts {
    fn eq(&self, other: &LabelledArcWithPorts) -> bool {
        self.src == other.src
            && self.dst == other.dst
            && match (&self.src_port, &other.src_port) {
                (Some(a), Some(b)) => a == b,
                (None, None) => true,
                _ => false,
            }
            && match (&self.dst_port, &other.dst_port) {
                (Some(a), Some(b)) => a == b,
                (None, None) => true,
                _ => false,
            }
            && match (&self.label, &other.label) {
                (Some(a), Some(b)) => a == b,
                (None, None) => true,
                _ => false,
            }
    }
}

impl Eq for LabelledArcWithPorts {}

impl Hash for LabelledArcWithPorts {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.src.hash(state);
        self.dst.hash(state);
        if let Some(a) = &self.src_port {
            a.hash(state);
        }
        if let Some(a) = &self.dst_port {
            a.hash(state);
        };
        if let Some(a) = &self.label {
            a.hash(state);
        };
    }
}

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DesignModelHeader {
    pub category: String,
    pub model_paths: Vec<String>,
    pub elements: Vec<String>,
    pub relations: Vec<LabelledArcWithPorts>,
}

impl PartialEq<DesignModelHeader> for DesignModelHeader {
    fn eq(&self, o: &DesignModelHeader) -> bool {
        self.category == o.category && self.elements == o.elements && self.relations == o.relations
    }
}

impl PartialOrd<DesignModelHeader> for DesignModelHeader {
    fn partial_cmp(&self, o: &DesignModelHeader) -> std::option::Option<std::cmp::Ordering> {
        let superset = o.elements.iter().all(|v| self.elements.contains(v))
            && o.relations.iter().all(|v| self.relations.contains(v));
        let subset = self.elements.iter().all(|v| o.elements.contains(v))
            && self.relations.iter().all(|v| o.relations.contains(v));
        return match (superset, subset) {
            (true, true) => Some(Ordering::Equal),
            (true, false) => Some(Ordering::Greater),
            (false, true) => Some(Ordering::Less),
            _ => None,
        };
    }
}

impl Eq for DesignModelHeader {}

impl Hash for DesignModelHeader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
        for m in &self.elements {
            m.hash(state);
        }
        for e in &self.relations {
            e.hash(state);
        }
    }
}

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DecisionModelHeader {
    pub category: String,
    pub body_path: Vec<String>,
    pub covered_elements: Vec<String>,
    pub covered_relations: Vec<LabelledArcWithPorts>,
}

impl PartialEq<DecisionModelHeader> for DecisionModelHeader {
    fn eq(&self, o: &DecisionModelHeader) -> bool {
        self.category == o.category
            && self.covered_elements == o.covered_elements
            && self.covered_relations == o.covered_relations
    }
}

impl PartialOrd<DecisionModelHeader> for DecisionModelHeader {
    fn partial_cmp(&self, o: &DecisionModelHeader) -> std::option::Option<std::cmp::Ordering> {
        let superset = o
            .covered_elements
            .iter()
            .all(|v| self.covered_elements.contains(v))
            && o.covered_relations
                .iter()
                .all(|v| self.covered_relations.contains(v));
        let subset = self
            .covered_elements
            .iter()
            .all(|v| o.covered_elements.contains(v))
            && self
                .covered_relations
                .iter()
                .all(|v| o.covered_relations.contains(v));
        return match (superset, subset) {
            (true, true) => Some(Ordering::Equal),
            (true, false) => Some(Ordering::Greater),
            (false, true) => Some(Ordering::Less),
            _ => None,
        };
    }
}

impl Eq for DecisionModelHeader {}

impl Hash for DecisionModelHeader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
        for m in &self.covered_elements {
            m.hash(state);
        }
        for e in &self.covered_elements {
            e.hash(state);
        }
    }
}
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

pub fn write_model_to_path<M: DecisionModel + Serialize>(
    m: &M,
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
        body_path: vec![target_path.to_str().unwrap().to_string()],
        covered_elements: h.covered_elements,
        covered_relations: h.covered_relations,
    }
}

pub type IdentificationRule =
    fn(Vec<Box<dyn DesignModel>>, Vec<Box<dyn DecisionModel>>) -> Vec<Box<dyn DecisionModel>>;

pub enum MarkedIdentificationRule {
    DesignModelOnlyIdentificationRule(IdentificationRule),
    DecisionModelOnlyIdentificationRule(IdentificationRule),
    SpecificDecisionModelIdentificationRule(Vec<String>, IdentificationRule),
    GenericIdentificationRule(IdentificationRule),
}

pub trait IdentificationModule {
    fn unique_identifier(&self) -> String;
    fn run_path(&self) -> &Path;
    fn identification_step(&self, iteration: i32) -> Vec<DecisionModelHeader>;
}

pub trait FullIdentificationModule {
    fn unique_identifier(&self) -> String;
    fn run_path(&self) -> &Path;
    fn decode_design_model(&self, m: &DesignModelHeader) -> Vec<Box<dyn DesignModel>>;
    fn decode_decision_model(&self, m: &DecisionModelHeader) -> Option<Box<dyn DecisionModel>>;
    fn identification_rules(&self) -> Vec<MarkedIdentificationRule>;
}

impl PartialEq<dyn IdentificationModule> for dyn IdentificationModule {
    fn eq(&self, other: &dyn IdentificationModule) -> bool {
        self.unique_identifier() == other.unique_identifier() && self.run_path() == other.run_path()
    }
}

impl Eq for dyn IdentificationModule {}

impl Hash for dyn IdentificationModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.unique_identifier().hash(state);
        self.run_path().hash(state);
    }
}

pub trait ExplorationModule {
    fn unique_identifier(&self) -> String;
    fn run_path(&self) -> &Path;
    fn available_criterias(&self, m: &dyn DecisionModel) -> HashMap<String, f32>;
    fn get_combination(&self, m: &dyn DecisionModel) -> ExplorationCombinationDescription;
    fn explore(&self, m: &dyn DecisionModel) -> &dyn Iterator<Item = &dyn DecisionModel>;
}

impl PartialEq<dyn ExplorationModule> for dyn ExplorationModule {
    fn eq(&self, other: &dyn ExplorationModule) -> bool {
        self.unique_identifier() == other.unique_identifier() && self.run_path() == other.run_path()
    }
}

impl Eq for dyn ExplorationModule {}

impl Hash for dyn ExplorationModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.unique_identifier().hash(state);
        self.run_path().hash(state);
    }
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ExplorationCombinationDescription {
    can_explore: bool,
    criteria: HashMap<String, f32>,
}

impl Hash for ExplorationCombinationDescription {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.can_explore.hash(state);
        for k in self.criteria.keys() {
            k.hash(state);
        }
    }
}

impl PartialEq<ExplorationCombinationDescription> for ExplorationCombinationDescription {
    fn eq(&self, other: &ExplorationCombinationDescription) -> bool {
        self.can_explore == other.can_explore && self.criteria == other.criteria
    }
}

impl Eq for ExplorationCombinationDescription {}

impl PartialOrd<ExplorationCombinationDescription> for ExplorationCombinationDescription {
    fn partial_cmp(&self, other: &ExplorationCombinationDescription) -> Option<Ordering> {
        if self.can_explore == other.can_explore {
            if self.criteria.keys().eq(other.criteria.keys()) {
                if self
                    .criteria
                    .iter()
                    .all(|(k, v)| v > other.criteria.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Greater);
                } else if self
                    .criteria
                    .iter()
                    .all(|(k, v)| v == other.criteria.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Equal);
                } else if self
                    .criteria
                    .iter()
                    .all(|(k, v)| v < other.criteria.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Less);
                }
            }
        }
        None
    }
}
