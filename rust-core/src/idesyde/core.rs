use std::collections::HashSet;

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct LabelledArcWithPorts {
    src: String,
    src_port: Option<String>,
    label: Option<String>,
    dst: String,
    dst_port: Option<String>,
}

#[derive(Serialize, Deserialize, PartialEq, Eq)]
pub struct DesignModelHeader {
    category: String,
    elements: HashSet<String>,
    model_paths: HashSet<String>,
    relations: HashSet<LabelledArcWithPorts>,
}

#[derive(Serialize, Deserialize, PartialEq, Eq)]
pub struct DecisionModelHeader {
    category: String,
    body_paths: HashSet<String>,
    covered_elements: HashSet<String>,
    covered_relations: HashSet<LabelledArcWithPorts>,
}

pub trait DesignModel {
    fn header(&self) -> DesignModelHeader;
}

pub trait DecisionModel {
    fn header(&self) -> DecisionModelHeader;

    fn dominates<O: DecisionModel>(&self, o: &O) -> bool {
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

// pub trait IdentificationRule<R: DecisionModel> {
//     fn identify(
//         design_models: HashSet<Box<dyn DesignModel>>,
//         decision_models: HashSet<Box<dyn DecisionModel>>,
//     ) -> (HashSet<Box<R>>);
// }

// enum MarkedIdentificationRule<R: DecisionModel> {
//     DesignModelOnlyIdentificationRule(
//         dyn Fn(HashSet<Box<dyn DesignModel>>, HashSet<Box<dyn DecisionModel>>) -> (HashSet<Box<R>>),
//     ),
// }
