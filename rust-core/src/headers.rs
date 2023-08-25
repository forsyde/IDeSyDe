use serde::{Deserialize, Serialize};
use std::{
    cmp::Ordering,
    collections::{HashMap, HashSet},
    hash::Hash,
    path::{Path, PathBuf},
};

use crate::DesignModel;

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DesignModelHeader {
    pub category: String,
    pub model_paths: Vec<String>,
    pub elements: HashSet<String>,
    // pub relations: Vec<LabelledArcWithPorts>,
}

impl DesignModelHeader {
    pub fn write_to_dir(&self, base_path: &Path, prefix: &str, suffix: &str) -> bool {
        let json_path = base_path.join(format!(
            "header_{}_{}_{}.json",
            prefix, self.category, suffix
        ));
        std::fs::write(
            &json_path,
            serde_json::to_string(self).expect("Failed to serialize decision model to json."),
        )
        .expect(
            format!(
                "Failed to write serialized design model to JSON {}.",
                json_path.display()
            )
            .as_str(),
        );
        std::fs::write(
            base_path.join(format!(
                "header_{}_{}_{}.msgpack",
                prefix, self.category, suffix
            )),
            rmp_serde::to_vec_named(self).expect("Failed to serialize design model to msgpack."),
        )
        .expect("Failed to write serialized design model during identification.");
        let cbor_path = base_path.join(format!(
            "header_{}_{}_{}.cbor",
            prefix, self.category, suffix
        ));
        let cbor_file = std::fs::File::create(&cbor_path)
            .expect("Failed to create file to deserialize CBOR design model header.");
        ciborium::into_writer(self, cbor_file).expect(
            format!(
                "Failed to serialize design model header to CBOR {}.",
                cbor_path.display()
            )
            .as_str(),
        );
        true
    }

    pub fn from_file(header_path: &Path) -> Option<DesignModelHeader> {
        if header_path
            .extension()
            .map(|x| x.eq_ignore_ascii_case("msgpack"))
            .unwrap_or(false)
        {
            let contents =
                std::fs::read(header_path).expect("Failed to read design model header file.");
            let header = rmp_serde::decode::from_slice(&contents)
                .expect("Failed to deserialize design model header.");
            Some(header)
        } else if header_path
            .extension()
            .map(|x| x.eq_ignore_ascii_case("cbor"))
            .unwrap_or(false)
        {
            let contents =
                std::fs::read(header_path).expect("Failed to read decision model header file.");
            let header = ciborium::from_reader(contents.as_slice())
                .expect("Failed to deserialize design model header.");
            Some(header)
        } else if header_path
            .extension()
            .map(|x| x.eq_ignore_ascii_case("json"))
            .unwrap_or(false)
        {
            let contents =
                std::fs::read(header_path).expect("Failed to read decision model header file.");
            let header = serde_json::from_reader(contents.as_slice())
                .expect("Failed to deserialize design model header.");
            Some(header)
        } else {
            None
        }
    }
}

impl DesignModel for DesignModelHeader {
    fn category(&self) -> String {
        self.category.to_owned()
    }

    fn header(&self) -> DesignModelHeader {
        self.to_owned()
    }
}

impl PartialEq<DesignModelHeader> for DesignModelHeader {
    fn eq(&self, o: &DesignModelHeader) -> bool {
        self.category == o.category && self.elements == o.elements // && self.relations == o.relations
    }
}

impl Eq for DesignModelHeader {}

impl PartialOrd<DesignModelHeader> for DesignModelHeader {
    fn partial_cmp(&self, o: &DesignModelHeader) -> std::option::Option<std::cmp::Ordering> {
        let superset = o.elements.iter().all(|v| self.elements.contains(v));
        // && o.relations.iter().all(|v| self.relations.contains(v));
        let subset = self.elements.iter().all(|v| o.elements.contains(v));
        // && self.relations.iter().all(|v| o.relations.contains(v));
        return match (superset, subset) {
            (true, true) => Some(Ordering::Equal),
            (true, false) => Some(Ordering::Greater),
            (false, true) => Some(Ordering::Less),
            _ => None,
        };
    }
}

impl Hash for DesignModelHeader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
        for m in &self.elements {
            m.hash(state);
        }
        // for e in &self.relations {
        //     e.hash(state);
        // }
    }
}

pub fn load_design_model_headers_from_binary(header_path: &Path) -> Vec<DesignModelHeader> {
    let mut design_models = Vec::new();
    // let known_decision_model_paths =
    if let Ok(ls) = header_path.read_dir() {
        for dir_entry in ls.flatten() {
            if dir_entry
                .path()
                .file_name()
                .and_then(|f| f.to_str())
                .map_or(false, |f| f.starts_with("header"))
            {
                if dir_entry
                    .path()
                    .extension()
                    .map(|x| x.eq_ignore_ascii_case("msgpack"))
                    .unwrap_or(false)
                {
                    let contents = std::fs::read(dir_entry.path())
                        .expect("Failed to read design model header file.");
                    let header = rmp_serde::decode::from_slice(&contents)
                        .expect("Failed to deserialize design model header.");
                    design_models.push(header);
                } else if dir_entry
                    .path()
                    .extension()
                    .map(|x| x.eq_ignore_ascii_case("cbor"))
                    .unwrap_or(false)
                {
                    let contents = std::fs::read(dir_entry.path())
                        .expect("Failed to read decision model header file.");
                    let header = ciborium::from_reader(contents.as_slice())
                        .expect("Failed to deserialize design model header.");
                    design_models.push(header);
                }
            }
        }
    }
    design_models
}

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DecisionModelHeader {
    pub category: String,
    pub body_path: Option<String>,
    pub covered_elements: HashSet<String>,
    // pub covered_relations: Vec<LabelledArcWithPorts>,
}

impl DecisionModelHeader {
    /// Utility function to generate the JSON string for this decision model header.
    /// Panics if there is a `serde` error during serialization.
    pub fn to_json_str(&self) -> String {
        serde_json::to_string(self).expect(
            format!(
                "Failed to serialize decision model header with category {} to json.",
                self.category
            )
            .as_str(),
        )
    }

    pub fn from_json_str(s: &str) -> Option<DecisionModelHeader> {
        serde_json::from_str(s).ok()
    }

    /// Utility function to write this header model in different formats in the `base_path`.
    /// Currently, the model is written in JSON, MsgPack and CBOR formats
    /// with the appropriate `header` conventional prefix _in addition_ to `prefix`.
    pub fn write_to_dir(&self, base_path: &Path, prefix: &str, suffix: &str) -> bool {
        std::fs::write(
            base_path.join(format!(
                "header_{}_{}_{}.json",
                prefix, self.category, suffix
            )),
            self.to_json_str(),
        )
        .expect("Failed to write serialized decision model during identification.");
        std::fs::write(
            base_path.join(format!(
                "header_{}_{}_{}.msgpack",
                prefix, self.category, suffix
            )),
            rmp_serde::to_vec_named(self).expect("Failed to serialize decision model to msgpack."),
        )
        .expect("Failed to write serialized decision model during identification.");
        let cbor_file = std::fs::File::create(base_path.join(format!(
            "header_{}_{}_{}.cbor",
            prefix, self.category, suffix
        )))
        .expect("Failed to create file to deserialize CBOR decision model header.");
        ciborium::into_writer(self, cbor_file)
            .expect("Failed to serialize decision model header during identification.");
        true
    }

    pub fn from_file(p: &Path) -> Option<DecisionModelHeader> {
        if let Ok(f) = std::fs::File::open(p) {
            // let inb = std::io::BufReader::new(f);
            if p.extension()
                .map(|ext| ext.eq_ignore_ascii_case("msgpack"))
                .unwrap_or(false)
            {
                // rmp_serde::from_read::<std::fs::File, DecisionModelHeader>(f).expect("Problems");
                // return None;
                return rmp_serde::from_read(f).ok();
            } else if p
                .extension()
                .map(|ext| ext.eq_ignore_ascii_case("cbor"))
                .unwrap_or(false)
            {
                return ciborium::from_reader(f).ok();
            } else if p
                .extension()
                .map(|ext| ext.eq_ignore_ascii_case("json"))
                .unwrap_or(false)
            {
                return serde_json::from_reader(f).ok();
            }
        }
        None
    }
}

impl PartialEq<DecisionModelHeader> for DecisionModelHeader {
    fn eq(&self, o: &DecisionModelHeader) -> bool {
        self.category == o.category && self.covered_elements == o.covered_elements
        // && self.covered_relations == o.covered_relations
    }
}

// impl Ord for DecisionModelHeader {
//     fn cmp(&self, o: &DecisionModelHeader) -> std::cmp::Ordering {
//         let superset = o
//             .covered_elements
//             .iter()
//             .all(|v| self.covered_elements.contains(v));
//         let subset = self
//             .covered_elements
//             .iter()
//             .all(|v| o.covered_elements.contains(v));
//         return match (superset, subset) {
//             (true, true) => Ordering::Equal,
//             (true, false) => Ordering::Greater,
//             (false, true) => Ordering::Less,
//             _ => self.category.cmp(&o.category),
//         };
//     }
// }

impl PartialOrd<DecisionModelHeader> for DecisionModelHeader {
    fn partial_cmp(&self, o: &DecisionModelHeader) -> Option<Ordering> {
        let superset = o
            .covered_elements
            .iter()
            .all(|v| self.covered_elements.contains(v));
        let subset = self
            .covered_elements
            .iter()
            .all(|v| o.covered_elements.contains(v));
        return match (self.category == o.category, superset, subset) {
            (true, true, true) => Some(Ordering::Equal),
            (true, true, false) => Some(Ordering::Greater),
            (true, false, true) => Some(Ordering::Less),
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
        // for e in &self.covered_elements {
        //     e.hash(state);
        // }
    }
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ExplorationBid {
    pub explorer_unique_identifier: String,
    pub can_explore: bool,
    pub properties: HashMap<String, f32>,
}

impl ExplorationBid {
    pub fn from_json_str(s: &str) -> Option<ExplorationBid> {
        serde_json::from_str(s).ok()
    }
}

impl Hash for ExplorationBid {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.explorer_unique_identifier.hash(state);
        self.can_explore.hash(state);
        for k in self.properties.keys() {
            k.hash(state);
        }
    }
}

impl PartialEq<ExplorationBid> for ExplorationBid {
    fn eq(&self, other: &ExplorationBid) -> bool {
        self.explorer_unique_identifier == other.explorer_unique_identifier
            && self.can_explore == other.can_explore
            && self.properties == other.properties
    }
}

impl Eq for ExplorationBid {}

impl PartialOrd<ExplorationBid> for ExplorationBid {
    fn partial_cmp(&self, other: &ExplorationBid) -> Option<Ordering> {
        if self.can_explore == other.can_explore {
            if self.properties.keys().eq(other.properties.keys()) {
                if self
                    .properties
                    .iter()
                    .all(|(k, v)| v > other.properties.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Greater);
                } else if self
                    .properties
                    .iter()
                    .all(|(k, v)| v == other.properties.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Equal);
                } else if self
                    .properties
                    .iter()
                    .all(|(k, v)| v < other.properties.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Less);
                }
            }
        }
        None
    }
}

pub fn load_decision_model_header_from_path(p: &Path) -> Option<DecisionModelHeader> {
    DecisionModelHeader::from_file(p)
    // if let Ok(f) = std::fs::File::open(p) {
    //     // let inb = std::io::BufReader::new(f);
    //     if p.extension()
    //         .map(|ext| ext.eq_ignore_ascii_case("msgpack"))
    //         .unwrap_or(false)
    //     {
    //         // rmp_serde::from_read::<std::fs::File, DecisionModelHeader>(f).expect("Problems");
    //         // return None;
    //         return rmp_serde::from_read(f).ok();
    //     } else if p
    //         .extension()
    //         .map(|ext| ext.eq_ignore_ascii_case("cbor"))
    //         .unwrap_or(false)
    //     {
    //         return ciborium::from_reader(f).ok();
    //     } else if p
    //         .extension()
    //         .map(|ext| ext.eq_ignore_ascii_case("json"))
    //         .unwrap_or(false)
    //     {
    //         return serde_json::from_reader(f).ok();
    //     }
    // }
    // None
}

pub fn load_decision_model_headers_from_binary(
    header_path: &Path,
) -> Vec<(PathBuf, DecisionModelHeader)> {
    let mut decision_models = Vec::new();
    // let known_decision_model_paths =
    if let Ok(ls) = header_path.read_dir() {
        for dir_entry in ls.flatten() {
            if dir_entry
                .path()
                .file_name()
                .and_then(|f| f.to_str())
                .map_or(false, |f| f.starts_with("header"))
            {
                if dir_entry
                    .path()
                    .extension()
                    .map(|x| x.eq_ignore_ascii_case("msgpack") || x.eq_ignore_ascii_case("cbor"))
                    .unwrap_or(false)
                {
                    let h = load_decision_model_header_from_path(dir_entry.path().as_path());
                    if let Some(header) = h {
                        decision_models.push((dir_entry.path(), header));
                    }
                }
            }
        }
    }
    decision_models
}
