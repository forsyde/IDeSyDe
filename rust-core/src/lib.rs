use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    path::Path,
};

use serde::{Deserialize, Serialize};
use std::cmp::Ordering;

#[derive(Serialize, Clone, Deserialize, PartialEq, Eq, Hash, Debug)]
pub struct LabelledArcWithPorts {
    src: String,
    src_port: Option<String>,
    label: Option<String>,
    dst: String,
    dst_port: Option<String>,
}

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DesignModelHeader {
    pub category: String,
    model_paths: HashSet<String>,
    elements: HashSet<String>,
    relations: HashSet<LabelledArcWithPorts>,
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
    covered_elements: HashSet<String>,
    covered_relations: HashSet<LabelledArcWithPorts>,
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
    fn unique_identifier(&self) -> &str;
    fn run_path(&self) -> &str;
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

pub trait Explorer {
    fn unique_identifier(&self) -> String;
    fn criterias(&self, m: &dyn DecisionModel) -> HashMap<String, f32>;
    fn can_explore(&self, m: &dyn DecisionModel) -> bool;
    fn explore(&self, m: &dyn DecisionModel) -> dyn Iterator<Item = dyn DecisionModel>;
    fn header(&self) -> ExplorerHeader;
}

#[derive(Debug, PartialEq, Eq, Hash, Deserialize, Serialize)]
pub struct ExplorerHeader {
    identifier: String,
}

pub trait ExplorationCombination {
    fn explorer(&self) -> &dyn Explorer;
    fn decision_model(&self) -> &dyn DecisionModel;
    fn header(&self) -> ExplorationCombinationheader;
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ExplorationCombinationheader {
    explorer_header: ExplorerHeader,
    decision_model_header: DecisionModelHeader,
    criteria: HashMap<String, f32>,
}

impl Hash for ExplorationCombinationheader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.explorer_header.hash(state);
        self.decision_model_header.hash(state);
        for k in self.criteria.keys() {
            k.hash(state);
        }
    }
}

impl PartialEq<ExplorationCombinationheader> for ExplorationCombinationheader {
    fn eq(&self, other: &ExplorationCombinationheader) -> bool {
        self.explorer_header == other.explorer_header
            && self.decision_model_header == other.decision_model_header
            && self.criteria == other.criteria
    }
}

impl Eq for ExplorationCombinationheader {}

impl PartialOrd<ExplorationCombinationheader> for ExplorationCombinationheader {
    fn partial_cmp(&self, other: &ExplorationCombinationheader) -> Option<Ordering> {
        if self.decision_model_header.category == other.decision_model_header.category {
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
