pub mod headers;
pub mod macros;

use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    io::BufWriter,
    path::Path,
    sync::Arc,
};

use derive_builder::Builder;
use downcast_rs::{impl_downcast, Downcast, DowncastSync};
use headers::{DecisionModelHeader, DesignModelHeader, ExplorationBid};
use serde::{
    de::{DeserializeOwned, Visitor},
    ser::{SerializeSeq, SerializeStruct},
    Deserialize, Serialize,
};
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

pub type IdentificationResult = (
    Box<dyn Iterator<Item = Arc<dyn DecisionModel>>>,
    Box<dyn Iterator<Item = String>>,
);

pub type IdentificationRule =
    fn(&HashSet<Arc<dyn DesignModel>>, &HashSet<Arc<dyn DecisionModel>>) -> IdentificationResult;

pub type ReverseIdentificationRule = fn(
    &HashSet<Arc<dyn DecisionModel>>,
    &HashSet<Arc<dyn DesignModel>>,
) -> Box<dyn Iterator<Item = Arc<dyn DesignModel>>>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MarkedIdentificationRule {
    DesignModelOnlyIdentificationRule(IdentificationRule),
    DecisionModelOnlyIdentificationRule(IdentificationRule),
    SpecificDecisionModelIdentificationRule(HashSet<String>, IdentificationRule),
    GenericIdentificationRule(IdentificationRule),
}

/// This trait is wrapper around the normal iteration to create a "session"
/// for identification modules. Via this, we can do more advanced things
/// that would otherwise be impossible with a simple function call or iterator,
/// like caching the decision or design models to not send them unnecesarily remotely.
///
/// Prefer to use `next_with_models` over `next` as it inserts the required models as
/// necessary in the internal state of this iterator.
pub trait IdentificationIterator: Iterator<Item = Arc<dyn DecisionModel>> + Sync {
    fn next_with_models(
        &mut self,
        decision_models: &HashSet<Arc<dyn DecisionModel>>,
        design_models: &HashSet<Arc<dyn DesignModel>>,
    ) -> Option<Arc<dyn DecisionModel>> {
        return None;
    }

    /// This method collect messages possibly produced during the identification session,
    /// e.g. errors, information or warnings, and returns it to the caller.
    ///
    /// The messages come in a (level_string, content_string) format.
    ///
    /// The trait shoud ensure that consumed messages are destroyed from the iterator.
    fn collect_messages(&mut self) -> Vec<(String, String)> {
        vec![]
    }
}

/// Identification modules are a thin layer on top of identification rules that facilitates treating
/// (reverse) identification rules within the orchestration process or remotely in the same fashion.
pub trait IdentificationModule: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn location_url(&self) -> Option<Url> {
        None
    }
    fn start_identification(
        &self,
        initial_design_models: &HashSet<Arc<dyn DesignModel>>,
        initial_decision_models: &HashSet<Arc<dyn DecisionModel>>,
    ) -> Box<dyn IdentificationIterator>;
    fn reverse_identification(
        &self,
        solved_decision_model: &HashSet<Arc<dyn DecisionModel>>,
        design_model: &HashSet<Arc<dyn DesignModel>>,
    ) -> Box<dyn Iterator<Item = Arc<dyn DesignModel>>>;
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
        self.location_url().hash(state);
    }
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

// #[derive(Clone, PartialEq, Eq)]
// pub struct ExplorationConfigurationBuilder {
//     max_sols: u64,
//     total_timeout: u64,
//     improvement_timeout: u64,
//     time_resolution: u64,
//     memory_resolution: u64,
//     improvement_iterations: u64,
//     strict: bool,
//     target_objectives: HashSet<String>,
// }

// impl ExplorationConfigurationBuilder {
//     pub fn new() -> ExplorationConfigurationBuilder {
//         ExplorationConfigurationBuilder {
//             max_sols: 0,
//             total_timeout: 0,
//             improvement_timeout: 0,
//             time_resolution: 0,
//             memory_resolution: 0,
//             improvement_iterations: 0,
//             strict: false,
//             target_objectives: HashSet::new(),
//         }
//     }

//     pub fn build(&self) -> ExplorationConfiguration {
//         ExplorationConfiguration {
//             max_sols: self.max_sols,
//             total_timeout: self.total_timeout,
//             improvement_timeout: self.improvement_timeout,
//             time_resolution: self.time_resolution,
//             memory_resolution: self.memory_resolution,
//             improvement_iterations: self.improvement_iterations,
//             strict: self.strict,
//             target_objectives: self.target_objectives.clone(),
//         }
//     }

//     pub fn max_sols(&mut self, val: u64) -> &mut Self {
//         self.max_sols = val;
//         self
//     }
//     pub fn total_timeout(&mut self, val: u64) -> &mut Self {
//         self.total_timeout = val;
//         self
//     }
//     pub fn improvement_timeout(&mut self, val: u64) -> &mut Self {
//         self.improvement_timeout = val;
//         self
//     }
//     pub fn time_resolution(&mut self, val: u64) -> &mut Self {
//         self.time_resolution = val;
//         self
//     }
//     pub fn memory_resolution(&mut self, val: u64) -> &mut Self {
//         self.memory_resolution = val;
//         self
//     }
//     pub fn improvement_iterations(&mut self, val: u64) -> &mut Self {
//         self.improvement_iterations = val;
//         self
//     }
//     pub fn strict(&mut self, val: bool) -> &mut Self {
//         self.strict = val;
//         self
//     }

//     pub fn target_objectives(&mut self, val: HashSet<String>) -> &mut Self {
//         self.target_objectives = val;
//         self
//     }

//     pub fn add_target_objectives(&mut self, val: String) -> &mut Self {
//         self.target_objectives.insert(val);
//         self
//     }
// }

impl ExplorationConfiguration {
    pub fn to_json_string(&self) -> String {
        serde_json::to_string(self)
            .expect("Failed to serialize a ExplorationConfiguration. Should never happen.")
    }
}

pub type ExplorationSolution = (Arc<dyn DecisionModel>, HashMap<String, f64>);

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
        _currrent_solutions: Vec<ExplorationSolution>,
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

impl<T: Explorer> Explorer for Arc<T> {
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
        _currrent_solutions: Vec<ExplorationSolution>,
        _exploration_configuration: ExplorationConfiguration,
    ) -> Box<dyn Iterator<Item = ExplorationSolution> + Send + Sync + '_> {
        self.as_ref()
            .explore(_m, _currrent_solutions, _exploration_configuration)
    }
}

/// The trait/interface for an exploration module that provides the API for exploration [1].
///
/// [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
///
pub trait ExplorationModule: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn location_url(&self) -> Option<Url> {
        None
    }

    fn explorers(&self) -> Vec<Arc<dyn Explorer>> {
        vec![]
    }
    // fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<(Arc<dyn Explorer>, ExplorationBid)> {
    //     self.explorers()
    //         .iter()
    //         .map(|x| (x.to_owned(), x.bid(&self.explorers(), m.clone())))
    //         .collect()
    // }
    // fn explore(
    //     &self,
    //     m: Arc<dyn DecisionModel>,
    //     explorer_id: &str,
    //     currrent_solutions: Vec<ExplorationSolution>,
    //     exploration_configuration: ExplorationConfiguration,
    // ) -> Box<dyn Iterator<Item = ExplorationSolution> + '_>;
    // fn explore_best(
    //     &self,
    //     m: Arc<dyn DecisionModel>,
    //     currrent_solutions: Vec<ExplorationSolution>,
    //     exploration_configuration: ExplorationConfiguration,
    // ) -> Box<dyn Iterator<Item = ExplorationSolution> + '_> {
    //     let bids = self.bid(m.clone());
    //     match compute_dominant_bidding(bids.iter()) {
    //         Some((_, bid)) => self.explore(
    //             m,
    //             bid.explorer_unique_identifier.as_str(),
    //             currrent_solutions,
    //             exploration_configuration,
    //         ),
    //         None => Box::new(std::iter::empty()) as Box<dyn Iterator<Item = ExplorationSolution>>,
    //     }
    // }
}

impl PartialEq<dyn ExplorationModule> for dyn ExplorationModule {
    fn eq(&self, other: &dyn ExplorationModule) -> bool {
        self.unique_identifier() == other.unique_identifier()
            && self.location_url() == other.location_url()
    }
}

impl Eq for dyn ExplorationModule {}

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
#[derive(Clone, PartialEq, Eq, Builder, Serialize, Deserialize)]
pub struct OpaqueDecisionModel {
    pub category: String,
    pub part: HashSet<String>,
    pub body_json: Option<String>,
    pub body_protobuf: Option<Vec<u8>>,
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
            body_protobuf: value.body_as_protobuf(),
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

impl<T: DecisionModel + ?Sized> From<Arc<T>> for OpaqueDecisionModel {
    fn from(value: Arc<T>) -> Self {
        OpaqueDecisionModel {
            category: value.category(),
            part: value.part().iter().map(|x| x.to_string()).collect(),
            body_json: value.body_as_json(),
            body_msgpack: value.body_as_msgpack(),
            body_cbor: value.body_as_cbor(),
            body_protobuf: value.body_as_protobuf(),
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
#[derive(Clone, PartialEq, Eq, Builder, Serialize, Deserialize)]
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
                .extension()
                .and_then(|x| x.to_str())
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

pub fn compute_dominant_biddings(biddings: &Vec<ExplorationBid>) -> Vec<(usize, ExplorationBid)> {
    if biddings.len() > 1 {
        biddings
            .iter()
            .enumerate()
            .filter(|(_, b)| {
                !biddings
                    .iter()
                    .filter(|bb| b != bb)
                    .any(|bb| b.partial_cmp(&bb) == Some(Ordering::Greater))
            })
            .map(|(i, b)| (i, b.to_owned()))
            .collect()
    } else {
        biddings
            .iter()
            .enumerate()
            .map(|(i, b)| (i, b.to_owned()))
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
    _m: &M,
    _currrent_solutions: Vec<ExplorationSolution>,
    _exploration_configuration: ExplorationConfiguration,
) -> (
    std::sync::mpsc::Receiver<ExplorationSolution>,
    std::sync::mpsc::Sender<bool>,
    std::thread::JoinHandle<()>,
)
where
    T: Explorer + Clone + ?Sized,
    M: Into<Arc<dyn DecisionModel>> + Clone,
{
    let (solution_tx, solution_rx) = std::sync::mpsc::channel();
    let (completed_tx, completed_rx) = std::sync::mpsc::channel();
    let this_explorer = explorer.clone();
    let this_decision_model = _m.to_owned().into();
    let handle = std::thread::spawn(move || {
        if let Ok(true) = completed_rx.recv_timeout(std::time::Duration::from_millis(300)) {
            return ();
        }
        for (solved_model, sol_objs) in this_explorer.explore(
            this_decision_model,
            _currrent_solutions.to_owned(),
            _exploration_configuration.to_owned(),
        ) {
            match solution_tx.send((solved_model, sol_objs)) {
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
