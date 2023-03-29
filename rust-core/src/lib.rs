use std::{collections::HashSet, hash::Hash, path::Path};

use serde::{Deserialize, Serialize};
use std::cmp::Ordering;

#[derive(Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct LabelledArcWithPorts {
    src: String,
    src_port: Option<String>,
    label: Option<String>,
    dst: String,
    dst_port: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct DesignModelHeader {
    category: String,
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
            } else if self == o {
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
    }
}

#[derive(Serialize, Deserialize)]
pub struct DecisionModelHeader {
    category: String,
    body_paths: HashSet<String>,
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
        if self.category == o.category {
            if self.covered_elements.is_superset(&o.covered_elements)
                && self.covered_relations.is_superset(&o.covered_relations)
            {
                return Some(Ordering::Greater);
            } else if self.covered_elements.is_subset(&o.covered_elements)
                && self.covered_relations.is_subset(&o.covered_relations)
            {
                return Some(Ordering::Less);
            } else if self == o {
                return Some(Ordering::Equal);
            }
        }
        None
    }
}

impl Eq for DecisionModelHeader {}

impl Hash for DecisionModelHeader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
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
    fn unique_identifier(&self) -> &str;
    fn run_path(&self) -> &Path;
    fn identify(&self, iteration: u64) -> HashSet<DecisionModelHeader>;
}

pub trait FullIdentificationModule {
    fn unique_identifier(&self) -> &str;
    fn run_path(&self) -> &str;
    fn decode_design_model(&self, m: &DesignModelHeader) -> HashSet<Box<dyn DesignModel>>;
    fn decode_decision_model(&self, m: &DecisionModelHeader) -> Option<Box<dyn DecisionModel>>;
    fn identification_rules(&self) -> HashSet<MarkedIdentificationRule>;
}
