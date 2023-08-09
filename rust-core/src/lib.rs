pub mod headers;

use std::{
    collections::HashSet,
    hash::Hash,
    path::{Path, PathBuf},
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
}
impl_downcast!(sync DesignModel);

impl PartialEq<dyn DesignModel> for dyn DesignModel {
    fn eq(&self, other: &Self) -> bool {
        self.header().eq(&other.header())
            && self.downcast_ref::<DesignModelHeader>().is_some()
                == other.downcast_ref::<DesignModelHeader>().is_some()
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

    fn write_to_dir(&self, p: &Path, prefix_str: &str, suffix_str: &str) -> DecisionModelHeader {
        let mut h = self.header();
        if let Some(j) = self.body_as_json() {
            let p = format!("body_{}_{}_{}.json", prefix_str, h.category, suffix_str);
            std::fs::write(&p, j).expect("Failed to write JSON body of decision model.");
            h.body_path = Some(p);
        }
        if let Some(b) = self.body_as_msgpack() {
            let p = format!("body_{}_{}_{}.msgpack", prefix_str, h.category, suffix_str);
            std::fs::write(&p, b).expect("Failed to write MsgPack body of decision model.");
            h.body_path = Some(p);
        }
        if let Some(b) = self.body_as_cbor() {
            let p = format!("body_{}_{}_{}.cbor", prefix_str, h.category, suffix_str);
            std::fs::write(&p, b).expect("Failed to write CBOR body of decision model.");
            h.body_path = Some(p);
        }
        h.write_to_dir(p, prefix_str, suffix_str);
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
        self.category() == other.category()
            && self.header() == other.header()
            && self.body_as_json().is_some() == other.body_as_json().is_some()
    }
}

impl PartialOrd<dyn DecisionModel> for dyn DecisionModel {
    fn partial_cmp(&self, other: &dyn DecisionModel) -> Option<Ordering> {
        self.header().partial_cmp(&other.header())
    }
}

pub type IdentificationRule =
    fn(&Vec<Box<dyn DesignModel>>, &Vec<Arc<dyn DecisionModel>>) -> Vec<Arc<dyn DecisionModel>>;

pub type ReverseIdentificationRule =
    fn(&Vec<Arc<dyn DecisionModel>>, &Vec<Box<dyn DesignModel>>) -> Vec<Box<dyn DesignModel>>;

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
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Vec<Arc<dyn DecisionModel>>;
    fn reverse_identification(
        &self,
        solved_decision_model: &Vec<Arc<dyn DecisionModel>>,
        design_model: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>>;
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
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>>;
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
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>>;
    fn explore_best(
        &self,
        m: Arc<dyn DecisionModel>,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>> {
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
            None => Box::new(std::iter::empty()),
        }
    }
}

impl PartialEq<dyn ExplorationModule> for dyn ExplorationModule {
    fn eq(&self, other: &dyn ExplorationModule) -> bool {
        self.unique_identifier() == other.unique_identifier()
    }
}

impl Eq for dyn ExplorationModule {}

pub struct StandaloneExplorationModule {
    unique_identifier: String,
    explorers: Vec<Box<dyn Explorer>>,
}

impl ExplorationModule for StandaloneExplorationModule {
    fn unique_identifier(&self) -> String {
        self.unique_identifier.to_owned()
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<ExplorationBid> {
        self.explorers.iter().map(|e| e.bid(m.clone())).collect()
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>> {
        self.explorers
            .iter()
            .find(|e| e.unique_identifier() == explorer_id)
            .map(|e| {
                e.explore(
                    m,
                    max_sols,
                    total_timeout,
                    time_resolution,
                    memory_resolution,
                )
            })
            .unwrap_or(Box::new(std::iter::empty::<Arc<dyn DecisionModel>>()))
    }
}

pub struct StandaloneIdentificationModule {
    unique_identifier: String,
    identification_rules: Vec<MarkedIdentificationRule>,
    reverse_identification_rules: Vec<ReverseIdentificationRule>,
    read_design_model: fn(path: &Path) -> Option<Box<dyn DesignModel>>,
    write_design_model: fn(design_model: &Box<dyn DesignModel>, dest: &Path) -> Vec<PathBuf>,
    decision_header_to_model: fn(header: &DecisionModelHeader) -> Option<Arc<dyn DecisionModel>>,
    pub decision_model_schemas: HashSet<String>,
}

impl StandaloneIdentificationModule {
    pub fn without_design_models(
        unique_identifier: &str,
        identification_rules: Vec<MarkedIdentificationRule>,
        reverse_identification_rules: Vec<ReverseIdentificationRule>,
        decision_header_to_model: fn(
            header: &DecisionModelHeader,
        ) -> Option<Arc<dyn DecisionModel>>,
        decision_model_schemas: HashSet<String>,
    ) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules,
            reverse_identification_rules,
            read_design_model: |_x| None,
            write_design_model: |_x, _p| vec![],
            decision_header_to_model,
            decision_model_schemas,
        };
    }

    pub fn complete(
        unique_identifier: &str,
        identification_rules: Vec<MarkedIdentificationRule>,
        reverse_identification_rules: Vec<ReverseIdentificationRule>,
        read_design_model: fn(path: &Path) -> Option<Box<dyn DesignModel>>,
        write_design_model: fn(design_model: &Box<dyn DesignModel>, dest: &Path) -> Vec<PathBuf>,
        decision_header_to_model: fn(
            header: &DecisionModelHeader,
        ) -> Option<Arc<dyn DecisionModel>>,
        decision_model_schemas: HashSet<String>,
    ) -> StandaloneIdentificationModule {
        return StandaloneIdentificationModule {
            unique_identifier: unique_identifier.to_owned(),
            identification_rules,
            reverse_identification_rules,
            read_design_model,
            write_design_model,
            decision_header_to_model,
            decision_model_schemas,
        };
    }

    pub fn read_design_model(&self, path: &Path) -> Option<Box<dyn DesignModel>> {
        return (self.read_design_model)(path);
    }
    pub fn write_design_model(
        &self,
        design_model: &Box<dyn DesignModel>,
        dest: &Path,
    ) -> Vec<PathBuf> {
        return (self.write_design_model)(design_model, dest);
    }
    pub fn decision_header_to_model(
        &self,
        header: &DecisionModelHeader,
    ) -> Option<Arc<dyn DecisionModel>> {
        return (self.decision_header_to_model)(header);
    }
}

impl IdentificationModule for StandaloneIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.unique_identifier.to_owned()
    }

    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Vec<Arc<dyn DecisionModel>> {
        let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
        for irule in &self.identification_rules {
            let f_opt = match irule {
                MarkedIdentificationRule::DesignModelOnlyIdentificationRule(f) => {
                    if iteration <= 0 {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::DecisionModelOnlyIdentificationRule(f) => {
                    if iteration > 0 {
                        Some(f)
                    } else {
                        None
                    }
                }
                MarkedIdentificationRule::GenericIdentificationRule(f) => Some(f),
                MarkedIdentificationRule::SpecificDecisionModelIdentificationRule(ms, f) => {
                    let categories: HashSet<String> =
                        identified.iter().map(|x| x.header().category).collect();
                    if ms.iter().all(|x| categories.contains(x)) {
                        Some(f)
                    } else {
                        None
                    }
                }
            };
            if let Some(f) = f_opt {
                for m in f(design_models, decision_models) {
                    if !identified.contains(&m) {
                        identified.push(m);
                    }
                }
            }
        }
        identified
    }

    fn reverse_identification(
        &self,
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>> {
        let mut reverse_identified: Vec<Box<dyn DesignModel>> = Vec::new();
        for f in &self.reverse_identification_rules {
            for m in f(solved_decision_models, design_models) {
                reverse_identified.push(m);
            }
        }
        reverse_identified
    }
}

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

#[macro_export]
macro_rules! decision_header_to_model_gen {
    ($($x:ty),*) => {
        |header: &idesyde_core::headers::DecisionModelHeader| {
            header.body_path.as_ref().and_then(|bp| {
                let bpath = std::path::PathBuf::from(bp);
                match header.category.as_str() {
                    $(
                        "$x" => load_decision_model::<$x>(&bpath)
                        .map(|m| Arc::new(m) as Arc<dyn DecisionModel>),
                    )*
                    _ => None,
                }
            })
        }
    };
}

#[macro_export]
macro_rules! decision_models_schemas_gen {
    ($($x:ty),*) => {
        HashSet::from([
            $(
                serde_json::to_string_pretty(&schema_for!($x)).unwrap(),
            )*
        ])
    };
}

#[macro_export]
macro_rules! impl_decision_model_standard_parts {
    ($x:ty) => {
        fn body_as_json(&self) -> Option<String> {
            serde_json::to_string(self).ok()
        }

        fn body_as_msgpack(&self) -> Option<Vec<u8>> {
            rmp_serde::to_vec(self).ok()
        }

        fn body_as_cbor(&self) -> Option<Vec<u8>> {
            let mut b: Vec<u8> = Vec::new();
            if let Ok(_) = ciborium::into_writer(self, &mut b) {
                Some(b)
            } else {
                None
            }
        }

        fn category(&self) -> String {
            "$x".to_string()
        }
    };
}
