pub mod headers;
pub mod macros;

use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
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
    pub time_resolution: u64,
    pub memory_resolution: u64,
}

impl ExplorationConfiguration {
    pub fn default() -> ExplorationConfiguration {
        ExplorationConfiguration {
            max_sols: 0,
            total_timeout: 0,
            time_resolution: 0,
            memory_resolution: 0,
        }
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
    fn bid(&self, m: Arc<dyn DecisionModel>) -> ExplorationBid;
    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Vec<ExplorationSolution>;
    fn iter_explore(
        &self,
        m: Arc<dyn DecisionModel>,
        currrent_solutions: Vec<ExplorationSolution>,
        solution_iter: fn(&ExplorationSolution) -> (),
        exploration_configuration: ExplorationConfiguration,
    ) -> Vec<ExplorationSolution>;
}
impl_downcast!(Explorer);

/// The trait/interface for an exploration module that provides the API for exploration [1].
///
/// [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
/// Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
/// Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
///
pub trait ExplorationModule: Send + Sync {
    fn unique_identifier(&self) -> String;
    fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<ExplorationBid>;
    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Vec<ExplorationSolution>;
    fn explore_best(
        &self,
        m: Arc<dyn DecisionModel>,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Vec<ExplorationSolution> {
        let bids = self.bid(m.clone());
        match compute_dominant_biddings(bids.iter()) {
            Some((_, bid)) => self.explore(
                m,
                bid.explorer_unique_identifier.as_str(),
                max_sols,
                total_timeout,
                time_resolution,
                memory_resolution,
            ),
            None => vec![],
        }
    }
    fn iter_explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        currrent_solutions: Vec<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
        solution_iter: fn(&ExplorationSolution) -> (),
    ) -> Vec<ExplorationSolution>;
    fn iter_explore_best(
        &self,
        m: Arc<dyn DecisionModel>,
        currrent_solutions: Vec<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
        solution_iter: fn(&ExplorationSolution) -> (),
    ) -> Vec<ExplorationSolution> {
        let bids = self.bid(m.clone());
        match compute_dominant_biddings(bids.iter()) {
            Some((_, bid)) => self.iter_explore(
                m,
                bid.explorer_unique_identifier.as_str(),
                currrent_solutions,
                exploration_configuration,
                solution_iter,
            ),
            None => vec![],
        }
    }
}

impl PartialEq<dyn ExplorationModule> for dyn ExplorationModule {
    fn eq(&self, other: &dyn ExplorationModule) -> bool {
        self.unique_identifier() == other.unique_identifier()
    }
}

impl Eq for dyn ExplorationModule {}

pub fn compute_dominant_biddings<'a, I>(biddings: I) -> Option<(usize, ExplorationBid)>
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
