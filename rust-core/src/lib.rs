use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    path::Path,
};

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
    model_paths: HashSet<String>,
    pub elements: HashSet<String>,
    pub relations: HashSet<LabelledArcWithPorts>,
}

impl PartialEq<DesignModelHeader> for DesignModelHeader {
    fn eq(&self, o: &DesignModelHeader) -> bool {
        self.category == o.category && self.elements == o.elements && self.relations == o.relations
    }
}

impl PartialOrd<DesignModelHeader> for DesignModelHeader {
    fn partial_cmp(&self, o: &DesignModelHeader) -> std::option::Option<std::cmp::Ordering> {
        if self.category == o.category {
            if self.elements.is_superset(&o.elements) && self.relations.is_superset(&o.relations) {
                return Some(Ordering::Greater);
            } else if self.elements.is_subset(&o.elements) && self.relations.is_subset(&o.relations)
            {
                return Some(Ordering::Less);
            } else {
                return Some(Ordering::Equal);
            }
        }
        None
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
    body_path: HashSet<String>,
    pub covered_elements: HashSet<String>,
    pub covered_relations: HashSet<LabelledArcWithPorts>,
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
        if self.covered_elements == o.covered_elements
            && self.covered_relations == o.covered_relations
        {
            return Some(Ordering::Equal);
        } else if self.covered_elements.is_superset(&o.covered_elements)
            && self.covered_relations.is_superset(&o.covered_relations)
        {
            return Some(Ordering::Greater);
        } else if self.covered_elements.is_subset(&o.covered_elements)
            && self.covered_relations.is_subset(&o.covered_relations)
        {
            return Some(Ordering::Less);
        } else {
            return None;
        }
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
    fn header(&self) -> DesignModelHeader;
}

pub trait DecisionModel {
    fn header(&self) -> DecisionModelHeader;

    fn dominates(&self, o: Box<dyn DecisionModel>) -> bool {
        self.header().category == o.header().category
            && self
                .header()
                .covered_elements
                .is_superset(&o.header().covered_elements)
            && self
                .header()
                .covered_relations
                .is_superset(&o.header().covered_relations)
    }
}

impl DecisionModel for DecisionModelHeader {
    fn header(&self) -> DecisionModelHeader {
        self.to_owned()
    }
}

pub type IdentificationRule = fn(
    HashSet<Box<dyn DesignModel>>,
    HashSet<Box<dyn DecisionModel>>,
) -> HashSet<Box<dyn DecisionModel>>;

pub enum MarkedIdentificationRule {
    DesignModelOnlyIdentificationRule(IdentificationRule),
    DecisionModelOnlyIdentificationRule(IdentificationRule),
    SpecificDecisionModelIdentificationRule(HashSet<String>, IdentificationRule),
    GenericIdentificationRule(IdentificationRule),
}

pub trait IdentificationModule {
    fn unique_identifier(&self) -> String;
    fn run_path(&self) -> &Path;
    fn identification_step(&self, iteration: i32) -> HashSet<DecisionModelHeader>;
}

pub trait FullIdentificationModule {
    fn unique_identifier(&self) -> String;
    fn run_path(&self) -> &Path;
    fn decode_design_model(&self, m: &DesignModelHeader) -> HashSet<Box<dyn DesignModel>>;
    fn decode_decision_model(&self, m: &DecisionModelHeader) -> Option<Box<dyn DecisionModel>>;
    fn identification_rules(&self) -> HashSet<MarkedIdentificationRule>;
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
