pub mod macros;

use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    path::Path,
    sync::{
        mpsc::{Receiver, Sender},
        Arc,
    },
    time::{Duration, Instant},
};

use derive_builder::Builder;
use downcast_rs::{impl_downcast, Downcast, DowncastSync};
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use sha2::{Digest, Sha512};
use std::cmp::Ordering;
use url::Url;

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
    /// The category that classifies this design model.
    fn category(&self) -> String;

    /// The format associated with this decision model. E.g. `fiodl` for ForSyDe IO files.
    fn format(&self) -> String {
        return "".to_string();
    }

    /// The set of identifiers for partially identified elements.
    fn elements(&self) -> HashSet<String> {
        return HashSet::new();
    }

    fn body_as_string(&self) -> Option<String> {
        None
    }

    fn write_to_dir(&self, base_path: &Path, prefix_str: &str, suffix_str: &str) {
        if let Some(j) = self.body_as_string() {
            let p = base_path.join(format!(
                    "body_{}_{}_{}.{}",
                    prefix_str,
                    self.category(),
                    suffix_str,
                    self.format()
                    ));
            std::fs::write(&p, j).expect("Failed to write body of design model.");
            // if let Some(s) = p.to_str().map(|x| x.to_string()) {
            //     h.model_paths.push(s);
            // }
        }
        // h.write_to_dir(base_path, prefix_str, suffix_str);
        // h
    }

    fn global_md5_hash(&self) -> Vec<u8> {
        let mut hasher = md5::Context::new();
        hasher.consume(self.format().as_bytes());
        hasher.consume(self.category().as_bytes());
        let elements = self.elements();
        let mut sorted = elements.iter().collect::<Vec<&String>>();
        sorted.sort();
        for e in sorted {
            hasher.consume(e.as_bytes());
        }
        hasher.compute().to_vec()
    }

    fn global_sha2_hash(&self) -> Vec<u8> {
        let mut hasher = Sha512::new();
        hasher.update(self.category().as_bytes());
        let part = self.elements();
        let mut sorted: Vec<&String> = part.iter().collect();
        sorted.sort();
        for e in sorted {
            hasher.update(e.as_bytes());
        }
        hasher.finalize().to_vec()
    }
}
impl_downcast!(sync DesignModel);

impl PartialEq<dyn DesignModel> for dyn DesignModel {
    fn eq(&self, other: &Self) -> bool {
        self.category() == other.category() && self.elements() == other.elements()
            // && self
            //     .body_as_string()
            //     .and_then(|b| other.body_as_string().map(|bb| b == bb))
            //     .unwrap_or(false)
    }
}

impl Eq for dyn DesignModel {}

impl Hash for dyn DesignModel {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category().hash(state);
        for e in self.elements() {
            e.hash(state);
        }
    }
}

impl<T: DesignModel> DesignModel for Arc<T> {
    fn category(&self) -> String {
        self.as_ref().category()
    }

    fn elements(&self) -> HashSet<String> {
        self.as_ref().elements()
    }

    fn format(&self) -> String {
        self.as_ref().format()
    }

    fn body_as_string(&self) -> Option<String> {
        self.as_ref().body_as_string()
    }
}

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
    fn category(&self) -> String;

    fn part(&self) -> HashSet<String> {
        HashSet::new()
    }

    fn body_as_json(&self) -> Option<String> {
        None
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        None
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        None
    }

    fn body_as_protobuf(&self) -> Option<Vec<u8>> {
        None
    }

    fn dominates(&self, o: &dyn DecisionModel) -> bool {
        self.category() == o.category() && self.part().is_superset(&o.part())
    }

    fn write_to_dir(&self, base_path: &Path, prefix_str: &str, suffix_str: &str) {
        // let mut h = self.header();
        if let Some(j) = self.body_as_json() {
            let p = base_path.join(format!(
                    "body_{}_{}_{}.json",
                    prefix_str,
                    self.category(),
                    suffix_str
                    ));
            std::fs::write(&p, j).expect("Failed to write JSON body of decision model.");
            // h.body_path = p.to_str().map(|x| x.to_string());
        }
        if let Some(b) = self.body_as_msgpack() {
            let p = base_path.join(format!(
                    "body_{}_{}_{}.msgpack",
                    prefix_str,
                    self.category(),
                    suffix_str
                    ));
            std::fs::write(&p, b).expect("Failed to write MsgPack body of decision model.");
            // h.body_path = p.to_str().map(|x| x.to_string());
        }
        if let Some(b) = self.body_as_cbor() {
            let p = base_path.join(format!(
                    "body_{}_{}_{}.cbor",
                    prefix_str,
                    self.category(),
                    suffix_str
                    ));
            std::fs::write(&p, b).expect("Failed to write CBOR body of decision model.");
            // h.body_path = p.to_str().map(|x| x.to_string());
        }
        // h.write_to_dir(base_path, prefix_str, suffix_str);
        // h
    }

    fn global_md5_hash(&self) -> Vec<u8> {
        let mut hasher = md5::Context::new();
        hasher.consume(self.category().as_bytes());
        let part = self.part();
        let mut sorted: Vec<&String> = part.iter().collect();
        sorted.sort();
        for e in sorted {
            hasher.consume(e.as_bytes());
        }
        hasher.compute().to_vec()
    }

    fn global_sha2_hash(&self) -> Vec<u8> {
        let mut hasher = Sha512::new();
        hasher.update(self.category().as_bytes());
        let part = self.part();
        let mut sorted: Vec<&String> = part.iter().collect();
        sorted.sort();
        for e in sorted {
            hasher.update(e.as_bytes());
        }
        hasher.finalize().to_vec()
    }
}
impl_downcast!(sync DecisionModel);

impl DecisionModel for Arc<dyn DecisionModel> {
    fn category(&self) -> String {
        self.as_ref().category()
    }

    fn part(&self) -> HashSet<String> {
        self.as_ref().part()
    }

    fn body_as_json(&self) -> Option<String> {
        self.as_ref().body_as_json()
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        self.as_ref().body_as_msgpack()
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        self.as_ref().body_as_cbor()
    }

    fn body_as_protobuf(&self) -> Option<Vec<u8>> {
        self.as_ref().body_as_protobuf()
    }
}

// impl DecisionModel for DecisionModelHeader {
//     fn category(&self) -> String {
//         self.category.to_owned()
//     }

//     fn header(&self) -> DecisionModelHeader {
//         self.to_owned()
//     }
// }

impl PartialEq<dyn DecisionModel> for dyn DecisionModel {
    fn eq(&self, other: &dyn DecisionModel) -> bool {
        self.category() == other.category() && self.part() == other.part()
            // && self.body_as_json() == other.body_as_json()
            // && self.body_as_cbor() == other.body_as_cbor()
            // && self.body_as_msgpack() == other.body_as_msgpack()
            // && self.body_as_protobuf() == other.body_as_protobuf()
    }
}

impl Eq for dyn DecisionModel {}

impl PartialOrd<dyn DecisionModel> for dyn DecisionModel {
    fn partial_cmp(&self, other: &dyn DecisionModel) -> Option<Ordering> {
        if self.category() == other.category() {
            let p = self.part();
            let op = other.part();
            if p == op {
                Some(Ordering::Equal)
            } else if p.is_superset(&op) {
                Some(Ordering::Greater)
            } else if p.is_subset(&op) {
                Some(Ordering::Less)
            } else {
                None
            }
        } else {
            None
        }
    }
}

impl Hash for dyn DecisionModel {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category().hash(state);
        for e in self.part() {
            e.hash(state);
        }
    }
}

pub type IdentificationResult = (Vec<Arc<dyn DecisionModel>>, Vec<String>);

pub type ReverseIdentificationResult = (Vec<Arc<dyn DesignModel>>, Vec<String>);

pub trait IdentificationRuleLike {

    fn identify(&self, design_models: &Vec<Arc<dyn DesignModel>>, decision_models: &Vec<Arc<dyn DecisionModel>>) -> IdentificationResult;

    fn uses_design_models(&self) -> bool {
        return true;
    }

    fn uses_decision_models(&self) -> bool {
        return true;
    }

    fn uses_specific_decision_models(&self) -> Option<Vec<String>> {
        return None;
    }

}

pub trait ReverseIdentificationRuleLike {

    fn reverse_identify(&self, decision_models: &Vec<Arc<dyn DecisionModel>>, design_models: &Vec<Arc<dyn DesignModel>>) -> ReverseIdentificationResult;

}

pub type IdentificationRule =
fn(&Vec<Arc<dyn DesignModel>>, &Vec<Arc<dyn DecisionModel>>) -> IdentificationResult;

pub type ReverseIdentificationRule =
fn(&Vec<Arc<dyn DecisionModel>>, &Vec<Arc<dyn DesignModel>>) -> Vec<Arc<dyn DesignModel>>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MarkedIdentificationRule {
    DesignModelOnlyIdentificationRule(IdentificationRule),
    DecisionModelOnlyIdentificationRule(IdentificationRule),
    SpecificDecisionModelIdentificationRule(HashSet<String>, IdentificationRule),
    GenericIdentificationRule(IdentificationRule),
}

#[derive(Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize, derive_builder::Builder)]
// #[builder(setter(each(name = "target_objectives")))]
pub struct ExplorationConfiguration {
    pub max_sols: u64,
    pub total_timeout: u64,
    pub improvement_timeout: u64,
    pub time_resolution: u64,
    pub memory_resolution: u64,
    pub improvement_iterations: u64,
    pub strict: bool,
    pub target_objectives: HashSet<String>,
}

impl ExplorationConfiguration {
    pub fn to_json_string(&self) -> Result<String, serde_json::Error> {
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
}

#[derive(Clone)]
pub struct ExplorationSolution {
    pub solved: Arc<dyn DecisionModel>,
    pub objectives: HashMap<String, f64>,
}

impl PartialEq<ExplorationSolution> for ExplorationSolution {
    fn eq(&self, other: &ExplorationSolution) -> bool {
        self.solved.as_ref() == other.solved.as_ref() && self.objectives == other.objectives
    }
}

impl Eq for ExplorationSolution {}

impl Hash for ExplorationSolution {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.solved.hash(state);
        for (k, _) in &self.objectives {
            k.hash(state);
        }
    }
}

impl From<ExplorationSolution> for (Arc<dyn DecisionModel>, HashMap<String, f64>) {
    fn from(value: ExplorationSolution) -> Self {
        (value.solved, value.objectives)
    }
}

impl From<&ExplorationSolution> for (Arc<dyn DecisionModel>, HashMap<String, f64>) {
    fn from(value: &ExplorationSolution) -> Self {
        (value.solved.to_owned(), value.objectives.to_owned())
    }
}

impl From<(Arc<dyn DecisionModel>, HashMap<String, f64>)> for ExplorationSolution {
    fn from(value: (Arc<dyn DecisionModel>, HashMap<String, f64>)) -> Self {
        ExplorationSolution {
            solved: value.0,
            objectives: value.1,
        }
    }
}

impl From<&(Arc<dyn DecisionModel>, HashMap<String, f64>)> for ExplorationSolution {
    fn from(value: &(Arc<dyn DecisionModel>, HashMap<String, f64>)) -> Self {
        ExplorationSolution {
            solved: value.0.to_owned(),
            objectives: value.1.to_owned(),
        }
    }
}

impl PartialOrd<ExplorationSolution> for ExplorationSolution {
    fn partial_cmp(&self, other: &ExplorationSolution) -> Option<Ordering> {
        let (_, lhs) = &self.into();
        let (_, rhs) = &other.into();
        if lhs.keys().all(|x| rhs.contains_key(x)) && rhs.keys().all(|x| lhs.contains_key(x)) {
            let mut all_equal = true;
            let mut less_exists = false;
            let mut greater_exists = false;
            for (k, v) in lhs {
                all_equal = all_equal && v == rhs.get(k).unwrap();
                less_exists = less_exists || v < rhs.get(k).unwrap();
                greater_exists = greater_exists || v > rhs.get(k).unwrap();
            }
            if all_equal {
                Some(Ordering::Equal)
            } else {
                match (less_exists, greater_exists) {
                    (true, false) => Some(Ordering::Less),
                    (false, true) => Some(Ordering::Greater),
                    _ => None,
                }
            }
            // if lhs.iter().all(|(k, v)| v == rhs.get(k).unwrap()) {
            //     Some(Ordering::Equal)
            // } else if lhs.iter().all(|(k, v)| v <= rhs.get(k).unwrap()) {
            //     Some(Ordering::Less)
            // } else if lhs.iter().all(|(k, v)| v >= rhs.get(k).unwrap()) {
            //     Some(Ordering::Greater)
            // } else {
            //     None
            // }
        } else {
            None
        }
    }
}

/// An exploration bidding captures the characteristics that an explorer
/// might display when exploring a decision model.
#[derive(Debug, Deserialize, Serialize, Clone, PartialEq)]
pub struct ExplorationBid {
    pub can_explore: bool,
    pub is_exact: bool,
    pub competitiveness: f32,
    pub target_objectives: HashSet<String>,
    pub additional_numeric_properties: HashMap<String, f32>,
}

impl ExplorationBid {
    pub fn from_json_str(s: &str) -> Option<ExplorationBid> {
        serde_json::from_str(s).ok()
    }

    pub fn impossible(_explorer_id: &str) -> ExplorationBid {
        ExplorationBid {
            can_explore: false,
            is_exact: false,
            competitiveness: 1.0,
            target_objectives: HashSet::new(),
            additional_numeric_properties: HashMap::new(),
        }
    }
}

impl Hash for ExplorationBid {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.can_explore.hash(state);
        for k in self.additional_numeric_properties.keys() {
            k.hash(state);
        }
    }
}

impl Eq for ExplorationBid {}

impl PartialOrd<ExplorationBid> for ExplorationBid {
    fn partial_cmp(&self, other: &ExplorationBid) -> Option<Ordering> {
        if self.can_explore == other.can_explore
            && self.is_exact == other.is_exact
                && self.target_objectives == other.target_objectives
                {
                    if (self.competitiveness - other.competitiveness).abs() <= 0.0001
                        && self
                            .additional_numeric_properties
                            .keys()
                            .eq(other.additional_numeric_properties.keys())
                            {
                                if self
                                    .additional_numeric_properties
                                        .iter()
                                        .all(|(k, v)| v > other.additional_numeric_properties.get(k).unwrap_or(v))
                                        {
                                            return Some(Ordering::Greater);
                                        } else if self
                                            .additional_numeric_properties
                                                .iter()
                                                .all(|(k, v)| v == other.additional_numeric_properties.get(k).unwrap_or(v))
                                                {
                                                    return Some(Ordering::Equal);
                                                } else if self
                                                    .additional_numeric_properties
                                                        .iter()
                                                        .all(|(k, v)| v < other.additional_numeric_properties.get(k).unwrap_or(v))
                                                        {
                                                            return Some(Ordering::Less);
                                                        }
                            }
                }
        None
    }
}

/// This trait is the root for all possible explorers within IDeSyDe. A real explorer should
/// implement this trait by dispatching the real exploration from 'explore'.
///
/// The Design model is left a type parameter because the explorer might be used in a context where
/// explorers for different decision models and design models are used together. A correct
/// implemention of the explorer should then:
///
///   1. if the DesignModel is part of the possible design models covered, it should return a
///      lazylist accodingly. 2. If the DesignModel si not part of the possible design models, then
///      the explorer should return an empty lazy list. 3. If the decision model is unexplorable
///      regardless, an empty list should be returned.
///
/// See [1] for some extra information on how the explorer fits the design space identifcation
/// approach, as well as [[idesyde.exploration.api.ExplorationHandler]] to see how explorers used
/// together in a generic context.
///
/// [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
///
pub trait Explorer: Downcast + Send + Sync {
    fn unique_identifier(&self) -> String;

    /// The location of this explorer. Empty if it is embedded.
    fn location_url(&self) -> Option<Url> {
        None
    }

    /// Give information about the exploration capabilities of this
    /// explorer for a decision model given that other explorers are present.
    fn bid(
        &self,
        _other_explorers: &Vec<Arc<dyn Explorer>>,
        _m: Arc<dyn DecisionModel>,
        ) -> ExplorationBid {
        ExplorationBid::impossible(&self.unique_identifier())
    }
    fn explore(
        &self,
        _m: Arc<dyn DecisionModel>,
        _currrent_solutions: &HashSet<ExplorationSolution>,
        _exploration_configuration: ExplorationConfiguration,
        ) -> Box<dyn Iterator<Item = ExplorationSolution> + Send + Sync + '_> {
        Box::new(std::iter::empty())
    }
}
impl_downcast!(Explorer);

impl PartialEq<dyn Explorer> for dyn Explorer {
    fn eq(&self, other: &dyn Explorer) -> bool {
        self.unique_identifier() == other.unique_identifier()
            && self.location_url() == other.location_url()
    }
}

impl Eq for dyn Explorer {}

impl Hash for dyn Explorer {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.unique_identifier().hash(state);
    }
}

impl<T: Explorer + ?Sized> Explorer for Arc<T> {
    fn unique_identifier(&self) -> String {
        self.as_ref().unique_identifier()
    }

    fn location_url(&self) -> Option<Url> {
        self.as_ref().location_url()
    }

    fn bid(
        &self,
        _other_explorers: &Vec<Arc<dyn Explorer>>,
        _m: Arc<dyn DecisionModel>,
        ) -> ExplorationBid {
        self.as_ref().bid(_other_explorers, _m)
    }

    fn explore(
        &self,
        _m: Arc<dyn DecisionModel>,
        _currrent_solutions: &HashSet<ExplorationSolution>,
        _exploration_configuration: ExplorationConfiguration,
        ) -> Box<dyn Iterator<Item = ExplorationSolution> + Send + Sync + '_> {
        self.as_ref()
            .explore(_m, _currrent_solutions, _exploration_configuration)
    }
}

/// An opaque model to exchange fundamental data about a decision model between different models in different languages.
///
/// This data record captures which elements of the target design models have been partially identified.
/// It provides a `category` to distinguish what type of decision model this is, so that different languages
/// can know which of their own data structures they should deserialize the decision model into.
///
/// Check the following paper for more in-depth definitions:
///
/// R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
#[derive(Clone, Builder, Serialize, Deserialize)]
pub struct OpaqueDecisionModel {
    pub category: String,
    pub part: HashSet<String>,
    pub body_json: Option<String>,
    pub body_msgpack: Option<Vec<u8>>,
    pub body_cbor: Option<Vec<u8>>,
}

impl OpaqueDecisionModel {
    pub fn from_json_str(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }

    pub fn from_msgpack(b: &[u8]) -> Result<Self, rmp_serde::decode::Error> {
        rmp_serde::from_slice(b)
    }

    pub fn from_cbor<R>(b: R) -> Result<Self, ciborium::de::Error<std::io::Error>>
        where
        R: std::io::Read,
        {
            ciborium::from_reader(b)
        }

    pub fn to_json(&self) -> Result<String, serde_json::Error> {
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
}

impl DecisionModel for OpaqueDecisionModel {
    fn category(&self) -> String {
        self.category.to_owned()
    }

    fn part(&self) -> HashSet<String> {
        self.part.iter().map(|x| x.to_string()).collect()
    }

    fn body_as_json(&self) -> Option<String> {
        self.body_json.to_owned()
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        self.body_msgpack.to_owned()
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        self.body_cbor.to_owned()
    }
}

impl<T: DecisionModel + ?Sized> From<&T> for OpaqueDecisionModel {
    fn from(value: &T) -> Self {
        OpaqueDecisionModel {
            category: value.category(),
            part: value.part().iter().map(|x| x.to_string()).collect(),
            body_json: value.body_as_json(),
            body_msgpack: value.body_as_msgpack(),
            body_cbor: value.body_as_cbor(),
        }
    }
}

impl Hash for OpaqueDecisionModel {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
        for x in self.part() {
            x.hash(state);
        }
    }
}

impl PartialEq<OpaqueDecisionModel> for OpaqueDecisionModel {
    fn eq(&self, other: &Self) -> bool {
        self.category == other.category && self.part == other.part
    }
}

impl Eq for OpaqueDecisionModel {}

impl<T: DecisionModel + ?Sized> From<Arc<T>> for OpaqueDecisionModel {
    fn from(value: Arc<T>) -> Self {
        OpaqueDecisionModel {
            category: value.category(),
            part: value.part().iter().map(|x| x.to_string()).collect(),
            body_json: value.body_as_json(),
            body_msgpack: value.body_as_msgpack(),
            body_cbor: value.body_as_cbor(),
        }
    }
}

/// An opaque model to exchange fundamental data about a design model between different models in different languages.
///
/// This data record captures which elements of the target design models taht can be partially identified.
/// It provides a `category` to distinguish what type of design model this is, so that different languages
/// can know which of their own data structures they should deserialize the design model into.
///
/// Check the following paper for more in-depth definitions:
///
/// R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
#[derive(Clone, Builder, Serialize, Deserialize)]
pub struct OpaqueDesignModel {
    pub category: String,
    pub elements: HashSet<String>,
    pub format: String,
    pub body: Option<String>,
}

impl OpaqueDesignModel {
    pub fn from_path_str(s: &str) -> OpaqueDesignModel {
        let path = Path::new(s);
        return path.into();
    }

    pub fn from_json_str(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }

    pub fn from_msgpack(b: &[u8]) -> Result<Self, rmp_serde::decode::Error> {
        rmp_serde::from_slice(b)
    }

    pub fn from_cbor<R>(b: R) -> Result<Self, ciborium::de::Error<std::io::Error>>
        where
        R: std::io::Read,
        {
            ciborium::from_reader(b)
        }

    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }

    pub fn to_cbor(&self) -> Result<Vec<u8>, ciborium::ser::Error<std::io::Error>> {
        let mut buf: Vec<u8> = Vec::new();
        ciborium::into_writer(self, buf.as_mut_slice())?;
        Ok(buf)
    }
}

impl<'a> From<&'a Path> for OpaqueDesignModel {
    fn from(value: &'a Path) -> Self {
        let path = if value.is_symlink() {
            value
                .read_link()
                .expect("Should not fail due to a symlink check condition.")
        } else {
            value.to_path_buf()
        };
        // let paths = path.map(|x| vec![x.to_string()]).unwrap_or(Vec::new());
        OpaqueDesignModel {
            elements: HashSet::new(),
            category: format!("Opaque({})", path.to_str().unwrap_or("")),
            format: path
                .file_name()
                .and_then(|x| x.to_str())
                .and_then(|x| x.split_once("."))
                .map(|(_, y)| y)
                .unwrap_or("")
                .to_string(),
                body: std::fs::read_to_string(path).ok(),
                // .and_then(|f|
                // }),
    }
}
}

// impl Serialize for OpaqueDesignModel {
//     fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
//     where
//         S: serde::Serializer,
//     {
//         serializer.serialize_str(self.category.as_str())?;
//         let mut elems_seq = serializer.serialize_seq(Some(self.elements.len()))?;
//         for s in &self.elements {
//             elems_seq.serialize_element(s)?;
//         }
//         elems_seq.end()?;
//         // serializer.serialize_i32(*self)
//     }
// }

impl<'a> From<&'a str> for OpaqueDesignModel {
    fn from(value: &'a str) -> Self {
        return OpaqueDesignModel::from_path_str(value);
    }
}

impl DesignModel for OpaqueDesignModel {
    fn category(&self) -> String {
        self.category.to_owned()
    }

    fn body_as_string(&self) -> Option<String> {
        self.body.to_owned()
    }

    fn format(&self) -> String {
        self.format.to_owned()
    }

    fn elements(&self) -> HashSet<String> {
        self.elements.iter().map(|x| x.to_owned()).collect()
    }

    fn write_to_dir(&self, base_path: &Path, prefix_str: &str, suffix_str: &str) {
        if let Some(j) = self.body_as_string() {
            let p = base_path.join(format!(
                    "body_{}_{}_{}.{}",
                    prefix_str,
                    self.category(),
                    suffix_str,
                    self.format()
                    ));
            std::fs::write(&p, j).expect("Failed to write body of design model.");
            // if let Some(s) = p.to_str().map(|x| x.to_string()) {
            //     h.model_paths.push(s);
            // }
        }
        // h.write_to_dir(base_path, prefix_str, suffix_str);
        // h
    }
}

impl<T: DesignModel + ?Sized> From<&T> for OpaqueDesignModel {
    fn from(value: &T) -> Self {
        OpaqueDesignModel {
            elements: value.elements().iter().map(|x| x.to_owned()).collect(),
            format: value.format(),
            body: value.body_as_string(),
            category: value.category(),
        }
    }
}

impl<T: DesignModel + ?Sized> From<Arc<T>> for OpaqueDesignModel {
    fn from(value: Arc<T>) -> Self {
        OpaqueDesignModel {
            elements: value.elements().iter().map(|x| x.to_owned()).collect(),
            format: value.format(),
            body: value.body_as_string(),
            category: value.category(),
        }
    }
}


/// This trait is wrapper around the normal iteration to create a "session"
/// for identification modules. Via this, we can do more advanced things
/// that would otherwise be impossible with a simple function call or iterator,
/// like caching the decision or design models to not send them unnecesarily remotely.
///
/// Prefer to use `next_with_models` over `next` as it inserts the required models as
/// necessary in the internal state of this iterator.
pub trait IdentificationIterator: Iterator<Item = IdentificationResult> + Sync {
    fn next_with_models(
        &mut self,
        _decision_models: &Vec<Arc<dyn DecisionModel>>,
        _design_models: &Vec<Arc<dyn DesignModel>>,
        ) -> Option<IdentificationResult> {
        return None;
    }

    // This method collect messages possibly produced during the identification session,
    // e.g. errors, information or warnings, and returns it to the caller.
    //
    // The messages come in a (level_string, content_string) format.
    //
    // The trait shoud ensure that consumed messages are destroyed from the iterator.
    // fn collect_messages(&mut self) -> Vec<(String, String)> {
    //     vec![]
    // }
}

/// A simple empty unit struct for an empty iterator
pub struct EmptyIdentificationIterator {}

impl Iterator for EmptyIdentificationIterator {
    type Item = IdentificationResult;

    fn next(&mut self) -> Option<Self::Item> {
        None
    }
}

impl IdentificationIterator for EmptyIdentificationIterator {}

/// Identification modules are a thin layer on top of identification rules that facilitates treating
/// (reverse) identification rules within the orchestration process or remotely in the same fashion.
pub trait Module: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn location_url(&self) -> Option<Url> {
        None
    }

    fn explorers(&self) -> Vec<Arc<dyn Explorer>> {
        Vec::new()
    }
    fn identification_rules(&self) -> Vec<Arc<dyn IdentificationRuleLike>> {
        vec![]
    }
    fn reverse_identification_rules(&self) -> Vec<Arc<dyn ReverseIdentificationRuleLike>> {
        vec![]
    }
    fn identification_step(
        &self,
        _decision_models: &Vec<Arc<dyn DecisionModel>>,
        _design_models: &Vec<Arc<dyn DesignModel>>,
        ) -> IdentificationResult {
        (vec![], vec![])
    }
    fn reverse_identification(
        &self,
        _solved_decision_model: &Vec<Arc<dyn DecisionModel>>,
        _design_model: &Vec<Arc<dyn DesignModel>>,
        ) -> Vec<Arc<dyn DesignModel>> {
        vec![]
    }
}

impl PartialEq<dyn Module> for dyn Module {
    fn eq(&self, other: &dyn Module) -> bool {
        self.unique_identifier() == other.unique_identifier()
    }
}

impl Eq for dyn Module {}

impl Hash for dyn Module {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.unique_identifier().hash(state);
        self.location_url().hash(state);
    }
}

/// This iterator is able to get a handful of explorers + decision models combination
/// and make the exploration cooperative. It does so by exchanging the solutions
/// found between explorers so that the explorers almost always with the latest approximate Pareto set
/// update between themselves.
pub struct CombinedExplorerIterator {
    sol_channels: Vec<Receiver<ExplorationSolution>>,
    is_exact: Vec<bool>,
    finish_request_channels: Vec<Sender<bool>>,
    duration_left: Option<Duration>,
    _handles: Vec<std::thread::JoinHandle<()>>,
}

impl CombinedExplorerIterator {
    pub fn start(
        explorers_and_models: &Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
        currrent_solutions: &HashSet<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
        ) -> CombinedExplorerIterator {
        let all_heuristic = explorers_and_models.iter().map(|_| false).collect();
        CombinedExplorerIterator::start_with_exact(
            explorers_and_models,
            &all_heuristic,
            currrent_solutions,
            exploration_configuration,
            )
    }

    pub fn start_with_exact(
        explorers_and_models: &Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
        is_exact: &Vec<bool>,
        currrent_solutions: &HashSet<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
        ) -> CombinedExplorerIterator {
        let mut sol_channels: Vec<Receiver<ExplorationSolution>> = Vec::new();
        let mut completed_channels: Vec<Sender<bool>> = Vec::new();
        let mut handles: Vec<std::thread::JoinHandle<()>> = Vec::new();
        for (e, m) in explorers_and_models {
            let (sc, cc, h) = explore_non_blocking(
                e,
                m,
                currrent_solutions,
                exploration_configuration.to_owned(),
                );
            sol_channels.push(sc);
            completed_channels.push(cc);
            handles.push(h);
        }
        CombinedExplorerIterator {
            sol_channels,
            is_exact: is_exact.to_owned(),
            finish_request_channels: completed_channels,
            duration_left: if exploration_configuration.improvement_timeout > 0u64 {
                Some(Duration::from_secs(
                        exploration_configuration.improvement_timeout,
                        ))
            } else {
                None
            },
            _handles: handles,
        }
    }
}

impl Drop for CombinedExplorerIterator {
    fn drop(&mut self) {
        // debug!("Killing iterator");
        for c in &self.finish_request_channels {
            match c.send(true) {
                Ok(_) => {}
                Err(_) => {}
            };
        }
    }
}

impl Iterator for CombinedExplorerIterator {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        let mut num_disconnected = 0;
        let start = Instant::now();
        while num_disconnected < self.sol_channels.len()
            && self
                .duration_left
                .map(|d| d >= start.elapsed())
                .unwrap_or(true)
                {
                    num_disconnected = 0;
                    for i in 0..self.sol_channels.len() {
                        match self.sol_channels[i].recv_timeout(std::time::Duration::from_millis(500)) {
                            Ok(solution) => {
                                // debug!("New solution from explorer index {}", i);
                                self.duration_left = self.duration_left.map(|d| {
                                    if d >= start.elapsed() {
                                        d - start.elapsed()
                                    } else {
                                        Duration::ZERO
                                    }
                                });
                                return Some(solution);
                            }
                            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                                num_disconnected += 1;
                                // finish early if the explorer is exact and ends early
                                if self.is_exact[i] {
                                    return None;
                                }
                            }
                            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
                        };
                    }
                }
        None
    }
}

pub struct MultiLevelCombinedExplorerIterator {
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    exploration_configuration: ExplorationConfiguration,
    // levels: Vec<CombinedExplorerIterator>,
    // levels_tuple: (Option<CombinedExplorerIterator>, CombinedExplorerIterator),
    levels_stream: (
        Option<Arc<Receiver<ExplorationSolution>>>,
        Arc<Receiver<ExplorationSolution>>,
        ),
        solutions: HashSet<ExplorationSolution>,
        // converged_to_last_level: bool,
        start: Instant,
}

impl Iterator for MultiLevelCombinedExplorerIterator {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        loop {
            if self.exploration_configuration.total_timeout > 0
                && self.start.elapsed()
                    > Duration::from_secs(self.exploration_configuration.total_timeout)
                    {
                        return None;
                    }
            let (_, last_level) = &self.levels_stream;
            match last_level.recv_timeout(Duration::from_millis(500)) {
                Ok(solution) => {
                    self.solutions.insert(solution.clone());
                    let sol_dominates = self
                        .solutions
                        .iter()
                        .any(|cur_sol| solution.partial_cmp(cur_sol) == Some(Ordering::Less));
                    if sol_dominates {
                        // debug!("Starting new level");
                        self.solutions.retain(|cur_sol| {
                            solution.partial_cmp(cur_sol) != Some(Ordering::Less)
                        });
                        let combined_explorer = CombinedExplorerIterator::start(
                            &self.explorers_and_models,
                            &self.solutions,
                            self.exploration_configuration.to_owned(),
                            );
                        let (sender, receiver) = std::sync::mpsc::channel::<ExplorationSolution>();
                        // move the data structures to contain new explorers
                        self.levels_stream = (Some(last_level.to_owned()), Arc::new(receiver));
                        // self.levels_tuple = (Some(self.levels_tuple.1), combined_explorer);
                        std::thread::spawn(move || {
                            for sol in combined_explorer {
                                match sender.send(sol) {
                                    Ok(_) => {}
                                    Err(_) => {
                                        break;
                                    }
                                };
                            }
                        });
                    }
                    // return if the solution is not dominated
                    if self
                        .solutions
                            .iter()
                            .all(|cur_sol| solution.partial_cmp(cur_sol) != Some(Ordering::Greater))
                            {
                                return Some(solution);
                            }
                }
                Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                    if let (Some(prev_level), _) = &self.levels_stream {
                        self.levels_stream = (None, prev_level.to_owned());
                        // self.levels_tuple = (
                        //     None,
                        //     self.levels_tuple
                        //         .0
                        //         .expect("Combined explorer should always exist."),
                        // );
                    } else {
                        return None;
                    }
                }
                _ => (),
            };
        }
        // None
        //     match self.levels_tuple {
        //         (_, Some(last_level)) => {

        //         }
        //     }
        // }
        // match self.levels.last_mut() {
        //     Some(last_level) => {
        //         match last_level
        //             .filter(|new_solution| {
        //                 // solution is not dominated
        //                 !self.solutions.iter().any(|cur_solution| {
        //                     new_solution.partial_cmp(cur_solution) == Some(Ordering::Greater)
        //                 })
        //             })
        //             .find(|x| !self.solutions.contains(x))
        //         {
        //             Some(new_solution) => {
        //                 self.solutions.insert(new_solution.clone());
        //                 if !self.converged_to_last_level {
        //                     let sol_dominates = self.solutions.iter().any(|cur_sol| {
        //                         new_solution.partial_cmp(cur_sol) == Some(Ordering::Less)
        //                     });
        //                     if sol_dominates {
        //                         // debug!("Starting new level");
        //                         self.solutions.retain(|cur_sol| {
        //                             new_solution.partial_cmp(cur_sol) != Some(Ordering::Less)
        //                         });
        //                         self.levels.push(CombinedExplorerIterator::start(
        //                             &self.explorers_and_models,
        //                             &self.solutions,
        //                             self.exploration_configuration.to_owned(),
        //                         ));
        //                     }
        //                     if self.levels.len() > 2 {
        //                         self.levels.remove(0);
        //                     }
        //                 }
        //                 // debug!("solutions {}", self.solutions.len());
        //                 return Some(new_solution);
        //                 // self.previous = Some(self.current_level);
        //                 // self.current_level
        //             }
        //             None => {
        //                 if !self.converged_to_last_level {
        //                     self.converged_to_last_level = true;
        //                     self.levels.remove(self.levels.len() - 1);
        //                     return self.next();
        //                 }
        //             }
        //         }
        //     }
        //     None => {}
        // };
        // None
}
}

pub fn explore_cooperatively_simple(
    explorers_and_models: &Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    currrent_solutions: &HashSet<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    // solution_inspector: F,
    ) -> MultiLevelCombinedExplorerIterator {
    let combined_explorer = CombinedExplorerIterator::start(
        &explorers_and_models,
        &currrent_solutions,
        exploration_configuration.to_owned(),
        );
    let (sender, receiver) = std::sync::mpsc::channel::<ExplorationSolution>();
    // move the data structures to contain new explorers
    let levels_stream = (None, Arc::new(receiver));
    // let levels_tuple = (None, combined_explorer);
    std::thread::spawn(move || {
        for sol in combined_explorer {
            match sender.send(sol) {
                Ok(_) => {}
                Err(_) => {}
            };
        }
    });
    MultiLevelCombinedExplorerIterator {
        explorers_and_models: explorers_and_models.clone(),
        solutions: currrent_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        // levels: vec![CombinedExplorerIterator::start_with_exact(
        //     explorers_and_models,
        //     &biddings.iter().map(|b| b.is_exact).collect(),
        //     currrent_solutions,
        //     exploration_configuration.to_owned(),
        // )],
        levels_stream,
        // converged_to_last_level: false,
        start: Instant::now(),
    }
}

pub fn explore_cooperatively(
    explorers_and_models: &Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    _biddings: &Vec<ExplorationBid>,
    currrent_solutions: &HashSet<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    // solution_inspector: F,
    ) -> MultiLevelCombinedExplorerIterator {
    let combined_explorer = CombinedExplorerIterator::start(
        &explorers_and_models,
        &currrent_solutions,
        exploration_configuration.to_owned(),
        );
    let (sender, receiver) = std::sync::mpsc::channel::<ExplorationSolution>();
    // move the data structures to contain new explorers
    let levels_stream = (None, Arc::new(receiver));
    // let levels_tuple = (None, combined_explorer);
    std::thread::spawn(move || {
        for sol in combined_explorer {
            match sender.send(sol) {
                Ok(_) => {}
                Err(_) => {}
            };
        }
    });
    MultiLevelCombinedExplorerIterator {
        explorers_and_models: explorers_and_models.clone(),
        solutions: currrent_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        // levels: vec![CombinedExplorerIterator::start_with_exact(
        //     explorers_and_models,
        //     &biddings.iter().map(|b| b.is_exact).collect(),
        //     currrent_solutions,
        //     exploration_configuration.to_owned(),
        // )],
        levels_stream,
        // converged_to_last_level: false,
        start: Instant::now(),
    }
}

pub fn compute_dominant_bidding<'a, I>(biddings: I) -> Option<(usize, ExplorationBid)>
where
I: Iterator<Item = &'a ExplorationBid>,
{
    biddings
        .enumerate()
        .reduce(|(i, b), (j, bb)| match b.partial_cmp(&bb) {
            Some(Ordering::Less) => (j, bb),
            _ => (i, b),
        })
    .map(|(i, b)| (i, b.to_owned()))
}

pub fn compute_dominant_identification(
    decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Vec<Arc<dyn DecisionModel>> {
    if decision_models.len() > 1 {
        decision_models
            .iter()
            .filter(|b| {
                !decision_models
                    .iter()
                    .filter(|bb| b != bb)
                    .all(|bb| b.partial_cmp(&bb) == Some(Ordering::Greater))
            })
        .map(|x| x.to_owned())
            .collect()
    } else {
        decision_models.iter().map(|x| x.to_owned()).collect()
    }
}

pub fn compute_dominant_biddings<M, E>(
    biddings: &Vec<(Arc<E>, Arc<M>, ExplorationBid)>,
    ) -> Vec<usize>
where
M: DecisionModel + PartialOrd + ?Sized,
E: Explorer + PartialEq + ?Sized,
{
    if biddings.len() > 1 {
        biddings
            .iter()
            .enumerate()
            .filter(|(_, (_, m, b))| {
                b.can_explore
                    && !biddings
                    .iter()
                    // .filter(|(_, mm, bb)| b != bb)
                    .any(|(_, mm, bb)| {
                        bb.can_explore
                            && (m.partial_cmp(&mm) == Some(Ordering::Less)
                                || (m.partial_cmp(&mm) != Some(Ordering::Less)
                                    && b.partial_cmp(&bb) == Some(Ordering::Greater)))
                    })
            })
        .map(|(i, _)| i)
            .collect()
    } else {
        biddings
            .iter()
            .enumerate()
            .filter(|(_, (_, _, b))| b.can_explore)
            .map(|(i, _)| i)
            .collect()
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

/// Perform exploration in a non blocking manner
///
/// This function effectively spawns a new thread and only kills it when the completed
/// signal is given. Note, however, that the thread can run for some time after the signal
/// is given will check for completion only after a solution has been found or
/// infeasibility has been proven.
pub fn explore_non_blocking<T, M>(
    explorer: &T,
    m: &M,
    currrent_solutions: &HashSet<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    ) -> (
        Receiver<ExplorationSolution>,
        Sender<bool>,
        std::thread::JoinHandle<()>,
        )
    where
    T: Explorer + Clone + ?Sized,
    M: Into<Arc<dyn DecisionModel>> + Clone,
{
    let (solution_tx, solution_rx) = std::sync::mpsc::channel();
    let (completed_tx, completed_rx) = std::sync::mpsc::channel();
    let this_explorer = explorer.clone();
    let this_decision_model = m.to_owned().into();
    let prev_sols = currrent_solutions.to_owned();
    let handle = std::thread::spawn(move || {
        if let Ok(true) = completed_rx.recv_timeout(std::time::Duration::from_millis(300)) {
            return ();
        }
        for new_solution in this_explorer.explore(
            this_decision_model,
            &prev_sols,
            exploration_configuration.to_owned(),
            ) {
            match solution_tx.send(new_solution) {
                Ok(_) => {}
                Err(_) => return (),
            };
        }
    });
    (solution_rx, completed_tx, handle)
}

pub fn pareto_dominance_partial_cmp(
    lhs: &HashMap<String, f64>,
    rhs: &HashMap<String, f64>,
    ) -> Option<Ordering> {
    if lhs.keys().all(|x| rhs.contains_key(x)) && rhs.keys().all(|x| lhs.contains_key(x)) {
        let mut all_equal = true;
        let mut less_exists = false;
        let mut greater_exists = false;
        for (k, v) in lhs {
            all_equal = all_equal && v == rhs.get(k).unwrap();
            less_exists = less_exists || v < rhs.get(k).unwrap();
            greater_exists = greater_exists || v > rhs.get(k).unwrap();
        }
        if all_equal {
            Some(Ordering::Equal)
        } else {
            match (less_exists, greater_exists) {
                (true, false) => Some(Ordering::Less),
                (false, true) => Some(Ordering::Greater),
                _ => None,
            }
        }
        // if lhs.iter().all(|(k, v)| v == rhs.get(k).unwrap()) {
        //     Some(Ordering::Equal)
        // } else if lhs.iter().all(|(k, v)| v <= rhs.get(k).unwrap()) {
        //     Some(Ordering::Less)
        // } else if lhs.iter().all(|(k, v)| v >= rhs.get(k).unwrap()) {
        //     Some(Ordering::Greater)
        // } else {
        //     None
        // }
    } else {
        None
    }
}

pub fn empty_identification_iter() -> EmptyIdentificationIterator {
    EmptyIdentificationIterator {}
}
