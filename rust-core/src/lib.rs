pub mod headers;
pub mod macros;

use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    net::{IpAddr, Ipv4Addr},
    path::Path,
    sync::Arc,
};

use downcast_rs::{impl_downcast, Downcast, DowncastSync};
use headers::{DecisionModelHeader, DesignModelHeader, ExplorationBid};
use serde::de::DeserializeOwned;
use std::cmp::Ordering;

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
    fn category(&self) -> String;

    fn header(&self) -> DesignModelHeader;

    fn body_as_string(&self) -> Option<String> {
        None
    }

    fn write_to_dir(
        &self,
        base_path: &Path,
        prefix_str: &str,
        suffix_str: &str,
    ) -> DesignModelHeader {
        let mut h = self.header();
        if let Some(j) = self.body_as_string() {
            let p = base_path.join(format!(
                "body_{}_{}_{}.txt",
                prefix_str, h.category, suffix_str
            ));
            std::fs::write(&p, j).expect("Failed to write JSON body of decision model.");
            if let Some(s) = p.to_str().map(|x| x.to_string()) {
                h.model_paths.push(s);
            }
        }
        h.write_to_dir(base_path, prefix_str, suffix_str);
        h
    }
}
impl_downcast!(sync DesignModel);

impl PartialEq<dyn DesignModel> for dyn DesignModel {
    fn eq(&self, other: &Self) -> bool {
        self.header().eq(&other.header())
            && self
                .body_as_string()
                .and_then(|b| other.body_as_string().map(|bb| b == bb))
                .unwrap_or(false)
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

    fn header(&self) -> DecisionModelHeader;

    fn body_as_json(&self) -> Option<String> {
        None
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        None
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        None
    }

    fn dominates(&self, o: Arc<dyn DecisionModel>) -> bool {
        match self.header().partial_cmp(&o.header()) {
            Some(Ordering::Greater) => true,
            _ => false,
        }
    }

    fn write_to_dir(
        &self,
        base_path: &Path,
        prefix_str: &str,
        suffix_str: &str,
    ) -> DecisionModelHeader {
        let mut h = self.header();
        if let Some(j) = self.body_as_json() {
            let p = base_path.join(format!(
                "body_{}_{}_{}.json",
                prefix_str, h.category, suffix_str
            ));
            std::fs::write(&p, j).expect("Failed to write JSON body of decision model.");
            h.body_path = p.to_str().map(|x| x.to_string());
        }
        if let Some(b) = self.body_as_msgpack() {
            let p = base_path.join(format!(
                "body_{}_{}_{}.msgpack",
                prefix_str, h.category, suffix_str
            ));
            std::fs::write(&p, b).expect("Failed to write MsgPack body of decision model.");
            h.body_path = p.to_str().map(|x| x.to_string());
        }
        if let Some(b) = self.body_as_cbor() {
            let p = base_path.join(format!(
                "body_{}_{}_{}.cbor",
                prefix_str, h.category, suffix_str
            ));
            std::fs::write(&p, b).expect("Failed to write CBOR body of decision model.");
            h.body_path = p.to_str().map(|x| x.to_string());
        }
        h.write_to_dir(base_path, prefix_str, suffix_str);
        h
    }
}
impl_downcast!(sync DecisionModel);

impl DecisionModel for DecisionModelHeader {
    fn category(&self) -> String {
        self.category.to_owned()
    }

    fn header(&self) -> DecisionModelHeader {
        self.to_owned()
    }
}

impl PartialEq<dyn DecisionModel> for dyn DecisionModel {
    fn eq(&self, other: &dyn DecisionModel) -> bool {
        self.category() == other.category() && self.header() == other.header()
    }
}

impl Eq for dyn DecisionModel {}

impl PartialOrd<dyn DecisionModel> for dyn DecisionModel {
    fn partial_cmp(&self, other: &dyn DecisionModel) -> Option<Ordering> {
        self.header().partial_cmp(&other.header())
    }
}

pub type IdentificationResult = (Vec<Arc<dyn DecisionModel>>, HashSet<String>);

pub type IdentificationRule =
    fn(&Vec<Arc<dyn DesignModel>>, &Vec<Arc<dyn DecisionModel>>) -> IdentificationResult;

pub type ReverseIdentificationRule =
    fn(&Vec<Arc<dyn DecisionModel>>, &Vec<Arc<dyn DesignModel>>) -> Vec<Arc<dyn DesignModel>>;

pub enum MarkedIdentificationRule {
    DesignModelOnlyIdentificationRule(IdentificationRule),
    DecisionModelOnlyIdentificationRule(IdentificationRule),
    SpecificDecisionModelIdentificationRule(Vec<String>, IdentificationRule),
    GenericIdentificationRule(IdentificationRule),
}

pub trait IdentificationModule: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Arc<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> IdentificationResult;
    fn reverse_identification(
        &self,
        solved_decision_model: &Vec<Arc<dyn DecisionModel>>,
        design_model: &Vec<Arc<dyn DesignModel>>,
    ) -> Vec<Arc<dyn DesignModel>>;
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

#[derive(Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct ExplorationConfiguration {
    pub max_sols: u64,
    pub total_timeout: u64,
    pub improvement_timeout: u64,
    pub time_resolution: u64,
    pub memory_resolution: u64,
    pub improvement_iterations: u64,
    pub strict: bool,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub struct ExplorationConfigurationBuilder {
    max_sols: u64,
    total_timeout: u64,
    improvement_timeout: u64,
    time_resolution: u64,
    memory_resolution: u64,
    improvement_iterations: u64,
    strict: bool,
}

impl ExplorationConfigurationBuilder {
    pub fn new() -> ExplorationConfigurationBuilder {
        ExplorationConfigurationBuilder {
            max_sols: 0,
            total_timeout: 0,
            improvement_timeout: 0,
            time_resolution: 0,
            memory_resolution: 0,
            improvement_iterations: 0,
            strict: false,
        }
    }

    pub fn build(&self) -> ExplorationConfiguration {
        ExplorationConfiguration {
            max_sols: self.max_sols,
            total_timeout: self.total_timeout,
            improvement_timeout: self.improvement_timeout,
            time_resolution: self.time_resolution,
            memory_resolution: self.memory_resolution,
            improvement_iterations: self.improvement_iterations,
            strict: self.strict,
        }
    }

    pub fn max_sols(&mut self, val: u64) -> &mut Self {
        self.max_sols = val;
        self
    }
    pub fn total_timeout(&mut self, val: u64) -> &mut Self {
        self.total_timeout = val;
        self
    }
    pub fn improvement_timeout(&mut self, val: u64) -> &mut Self {
        self.improvement_timeout = val;
        self
    }
    pub fn time_resolution(&mut self, val: u64) -> &mut Self {
        self.time_resolution = val;
        self
    }
    pub fn memory_resolution(&mut self, val: u64) -> &mut Self {
        self.memory_resolution = val;
        self
    }
    pub fn improvement_iterations(&mut self, val: u64) -> &mut Self {
        self.improvement_iterations = val;
        self
    }
    pub fn strict(&mut self, val: bool) -> &mut Self {
        self.strict = val;
        self
    }
}

impl ExplorationConfiguration {
    pub fn to_json_string(&self) -> String {
        serde_json::to_string(self)
            .expect("Failed to serialize a ExplorationConfiguration. Should never happen.")
    }
}

pub type ExplorationSolution = (Arc<dyn DecisionModel>, HashMap<String, f64>);

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
    fn location_url(&self) -> IpAddr {
        std::net::IpAddr::V4(Ipv4Addr::LOCALHOST)
    }
    fn location_port(&self) -> usize {
        0
    }
    fn bid(&self, _m: Arc<dyn DecisionModel>) -> ExplorationBid {
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

/// Perform exploration in a non blkcing manner
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

impl PartialEq<dyn Explorer> for dyn Explorer {
    fn eq(&self, other: &dyn Explorer) -> bool {
        self.unique_identifier() == other.unique_identifier()
            && self.location_url() == other.location_url()
            && self.location_port() == other.location_port()
    }
}

impl Eq for dyn Explorer {}

impl Explorer for Arc<dyn Explorer> {
    fn unique_identifier(&self) -> String {
        self.as_ref().unique_identifier()
    }

    fn location_url(&self) -> IpAddr {
        self.as_ref().location_url()
    }

    fn location_port(&self) -> usize {
        self.as_ref().location_port()
    }

    fn bid(&self, _m: Arc<dyn DecisionModel>) -> ExplorationBid {
        self.as_ref().bid(_m)
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
    fn location_url(&self) -> IpAddr {
        std::net::IpAddr::V4(Ipv4Addr::LOCALHOST)
    }
    fn location_port(&self) -> usize {
        0
    }
    fn explorers(&self) -> Vec<Arc<dyn Explorer>> {
        vec![]
    }
    fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<(Arc<dyn Explorer>, ExplorationBid)> {
        self.explorers()
            .iter()
            .map(|x| (x.to_owned(), x.bid(m.clone())))
            .collect()
    }
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
            && self.location_port() == other.location_port()
    }
}

impl Eq for dyn ExplorationModule {}

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
