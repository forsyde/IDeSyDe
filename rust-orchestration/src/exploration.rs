use std::{
    cmp::Ordering,
    collections::{HashMap, HashSet, VecDeque},
    io::{BufRead, BufReader},
    path::PathBuf,
    process::{Child, Stdio},
    rc::Rc,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use derive_builder::Builder;
use idesyde_blueprints::ExplorationSolutionMessage;
use idesyde_core::{
    DecisionModel, ExplorationBid, ExplorationConfiguration, ExplorationConfigurationBuilder,
    ExplorationSolution, Explorer, Module, OpaqueDecisionModel,
};
use log::{debug, warn};
use reqwest::blocking::multipart::Form;
use serde::{de, Deserialize, Serialize};
use url::Url;

use rayon::prelude::*;

#[derive(Deserialize, Serialize, PartialEq, Clone)]
pub struct ExplorerBidding {
    explorer_unique_identifier: String,
    can_explore: bool,
    properties: HashMap<String, f64>,
}

impl TryFrom<&str> for ExplorerBidding {
    type Error = serde_json::Error;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        serde_json::from_str(value)
    }
}
#[derive(Deserialize, Serialize, PartialEq, Clone, Builder)]
pub struct ExplorationRequestMessage {
    model_message: OpaqueDecisionModel,
    previous_solutions: Vec<ExplorationSolutionMessage>,
    configuration: ExplorationConfiguration,
}

impl ExplorationRequestMessage {
    pub fn from<T: DecisionModel + ?Sized>(decision_model: &T) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            model_message: OpaqueDecisionModel::from(decision_model),
            previous_solutions: vec![],
            configuration: ExplorationConfigurationBuilder::default()
                .build()
                .expect("Failed to buuild exploration configuraiton. Should never fail."),
        }
    }

    pub fn to_json_str(&self) -> String {
        serde_json::to_string(self).expect("Failed to serialize but shoudl always succeed")
    }
}

impl TryFrom<&str> for ExplorationRequestMessage {
    type Error = serde_json::Error;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        serde_json::from_str(value)
    }
}

pub struct ExternalExplorerSolutionIter {
    pub websocket: tungstenite::WebSocket<std::net::TcpStream>,
}

impl ExternalExplorerSolutionIter {
    /// This creation function assumes that the channel is already sending back solutions
    pub fn new<I>(c: I) -> ExternalExplorerSolutionIter
    where
        I: Into<tungstenite::WebSocket<std::net::TcpStream>>,
    {
        return ExternalExplorerSolutionIter {
            websocket: c.into(),
        };
    }
}

impl Iterator for ExternalExplorerSolutionIter {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        if self.websocket.can_read() {
            // while let is here in order to discard keep
            while let Ok(message) = self.websocket.read() {
                match message {
                    tungstenite::Message::Text(txt) => {
                        if txt.eq_ignore_ascii_case("done") {
                            // debug!("Received done");
                            return None;
                        } else if let Ok(sol) = ExplorationSolutionMessage::from_json_str(&txt) {
                            return Some(ExplorationSolution {
                                solved: Arc::new(OpaqueDecisionModel::from(&sol))
                                    as Arc<dyn DecisionModel>,
                                objectives: sol.objectives,
                            });
                        } else if let Err(e) = ExplorationSolutionMessage::from_json_str(&txt) {
                            debug!(
                                "Failed to deserialize exploration solution message: {}",
                                e.to_string()
                            );
                        }
                    }
                    tungstenite::Message::Binary(sol_cbor) => {
                        if let Ok(sol) = ExplorationSolutionMessage::from_cbor(sol_cbor.as_slice())
                        {
                            return Some(ExplorationSolution {
                                solved: Arc::new(OpaqueDecisionModel::from(&sol))
                                    as Arc<dyn DecisionModel>,
                                objectives: sol.objectives,
                            });
                        }
                    }
                    tungstenite::Message::Ping(_) => {
                        if let Err(e) = self.websocket.send(tungstenite::Message::Pong(vec![])) {
                            debug!("Failed to send a pong within exploration. Likely a failure occurred.");
                            debug!("Message was: {}", e.to_string());
                        };
                    }
                    tungstenite::Message::Pong(_) => {
                        if let Err(e) = self.websocket.send(tungstenite::Message::Ping(vec![])) {
                            debug!("Failed to send a ping within exploration. Likely a failure occurred.");
                            debug!("Message was: {}", e.to_string());
                        };
                    }
                    _ => (),
                }
            }
        }
        None
    }
}

impl Drop for ExternalExplorerSolutionIter {
    fn drop(&mut self) {
        if self.websocket.can_write() {
            if let Err(e) = self.websocket.close(None) {
                debug!("Failed to close websocket. Likely a failure occurred.");
                debug!("Message was: {}", e.to_string());
            };
        }
    }
}

#[derive(Builder, Clone)]
pub struct ExternalExplorer {
    name: String,
    url: Url,
    client: Arc<reqwest::blocking::Client>,
}

impl Explorer for ExternalExplorer {
    fn unique_identifier(&self) -> String {
        self.name.to_owned()
    }

    fn location_url(&self) -> std::option::Option<url::Url> {
        Some(self.url.to_owned())
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> ExplorationBid {
        let model_hash = m.global_sha2_hash();
        let exists = self
            .url
            .join("/decision/cache/exists")
            .ok()
            .and_then(|u| self.client.get(u).body(model_hash.clone()).send().ok())
            .and_then(|r| r.text().ok())
            .map(|t| t.eq_ignore_ascii_case("true"))
            .unwrap_or(false);
        if !exists {
            // debug!("{} is not in cache for {}. Adding it with {:?}.", m.category(), self.unique_identifier(), m.global_sha2_hash());
            if let Ok(json_str) = OpaqueDecisionModel::from(m).to_json() {
                if let Ok(r) = self
                    .client
                    .post(self.url.join("/decision/cache/add").unwrap())
                    .multipart(Form::new().text("decisionModel", json_str))
                    .send()
                {
                    // debug!("Added decision model to cache: {:?}", r.bytes().unwrap());
                    debug!("{}", r.text().unwrap());
                };
            }
        }
        if let Ok(bid_url) = self.url.join(format!("/{}/bid", self.name).as_str()) {
            match self.client.get(bid_url).body(model_hash).send() {
                Ok(result) => match result.text() {
                    Ok(text) => {
                        if !text.is_empty() {
                            match ExplorationBid::from_json_str(&text) {
                                Some(bid) => return bid,
                                None => {
                                    debug!(
                                        "failed to deserialize exploration bid message {}",
                                        &text
                                    );
                                }
                            }
                        }
                    }
                    Err(err) => {
                        debug!(
                            "Explorer {} failed to transform into text with: {}",
                            self.unique_identifier(),
                            err.to_string()
                        );
                    }
                },
                Err(err) => {
                    debug!(
                        "Explorer {} failed to request with: {}",
                        self.unique_identifier(),
                        err.to_string()
                    );
                }
            }
        }
        ExplorationBid::impossible()
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        currrent_solutions: &HashSet<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
    ) -> Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>> {
        let mut mut_url = self.url.clone();
        if let Err(_) = mut_url.set_scheme("ws") {
            warn!(
                "Failed to set up exploration websocket. Module {} is likely to fail exploration.",
                self.unique_identifier()
            );
        };
        if let Ok(explore_url) =
            mut_url.join(format!("/{}/explore", self.unique_identifier()).as_str())
        {
            if let Ok(explore_sckt) = explore_url.socket_addrs(|| None) {
                if let Some((mut ws, _)) = std::net::TcpStream::connect(explore_sckt[0])
                    .ok()
                    .and_then(|stream| tungstenite::client(explore_url, stream).ok())
                {
                    if let Ok(design_cbor) = OpaqueDecisionModel::from(m).to_json() {
                        if let Err(e) = ws.send(tungstenite::Message::text(design_cbor)) {
                            warn!("Failed to send decision model to {} for exploration. Trying to proceed anyway.", self.unique_identifier());
                            debug!("Message was: {}", e.to_string());
                        };
                    };
                    for prev_sol in currrent_solutions {
                        if let Ok(design_cbor) =
                            ExplorationSolutionMessage::from(prev_sol).to_json_str()
                        {
                            if let Err(e) = ws.send(tungstenite::Message::text(design_cbor)) {
                                warn!("Failed to send previous solution to {} for exploration. Trying to proceed anyway.", self.unique_identifier());
                                debug!("Message was: {}", e.to_string());
                            };
                        };
                    }
                    if let Ok(conf_cbor) = exploration_configuration.to_json_string() {
                        if let Err(e) = ws.send(tungstenite::Message::text(conf_cbor)) {
                            warn!("Failed to send configuration to {} for exploration. Trying to proceed anyway.", self.unique_identifier());
                            debug!("Message was: {}", e.to_string());
                        };
                    }
                    if let Err(e) = ws.send(tungstenite::Message::text("done")) {
                        warn!("Failed to send exploration request to {} for exploration. Exploration is likely to fail.", self.unique_identifier());
                        debug!("Message was: {}", e.to_string());
                    };
                    return Arc::new(Mutex::new(ExternalExplorerSolutionIter::new(ws)));
                }
            } else {
                warn!("Failed to open exploration connetion. Trying to proceed anyway.");
            }
        }
        // if let Ok(explore_url) = self
        //     .url
        //     .join(format!("/{}/explore", self.name).as_str())
        //     .and_then(|u| u.join("/explore"))
        // {
        //     let mut form = reqwest::blocking::multipart::Form::new();
        //     form = form.text(
        //         "decisionModel",
        //         OpaqueDecisionModel::from(m)
        //             .to_json()
        //             .expect("Failed to make Json out of opaque decision model. Should never fail."),
        //     );
        //     form = form.text("configuration", exploration_configuration.to_json_string());
        //     for (i, sol) in currrent_solutions.iter().enumerate() {
        //         form = form.text(
        //             format!("previousSolution{}", i),
        //             ExplorationSolutionMessage::from(sol).to_json_str(),
        //         );
        //     }
        // }
        // match std::net::TcpStream::connect(format!("{}:{}", self.address, self.port).as_str()) {
        //     Ok(stream) => {
        //         stream
        //             .set_read_timeout(None)
        //             .expect("Failed to set read timeout to infinite. Should never fail.");
        //         stream
        //             .set_write_timeout(None)
        //             .expect("Failed to set write timeout to infinite. Should never fail.");
        //         match tungstenite::client(
        //             format!(
        //                 "ws://{}:{}/{}/explore",
        //                 self.address,
        //                 self.port,
        //                 self.unique_identifier()
        //             )
        //             .as_str(),
        //             stream,
        //         ) {
        //             Ok((mut conn, _)) => {
        //                 for sol in &currrent_solutions {
        //                     if let Err(e) = conn.send(tungstenite::Message::text(
        //                         ExplorationSolutionMessage::from(sol).to_json_str(),
        //                     )) {
        //                         warn!("Failed to send previous solution to explorer {}. Trying to continue anyway.", self.unique_identifier());
        //                         debug!("Sending error: {}", e.to_string());
        //                     };
        //                 }
        //                 if let Err(e) = conn.send(tungstenite::Message::text(
        //                     exploration_configuration.to_json_string(),
        //                 )) {
        //                     warn!("Failed to send exploration configuration to explorer {}. Trying to continue anyway.", self.unique_identifier());
        //                     debug!("Sending error: {}", e.to_string());
        //                 }
        //                 if let Ok(_) = conn.send(tungstenite::Message::text(
        //                     DecisionModelMessage::from(m).to_json_str(),
        //                 )) {
        //                     return Box::new(ExternalExplorerSolutionIter::new(conn));
        //                 }
        //             }
        //             Err(e) => {
        //                 debug!(
        //                     "Failed to handshake with {}: {}",
        //                     self.unique_identifier(),
        //                     e.to_string()
        //                 );
        //             }
        //         }
        //     }
        //     Err(e) => {
        //         debug!(
        //             "Failed to create connection with {}: {}",
        //             self.unique_identifier(),
        //             e.to_string()
        //         );
        //     }
        // }
        Arc::new(Mutex::new(std::iter::empty()))
    }
}

/// This iterator is able to get a handful of explorers + decision models combination
/// and make the exploration cooperative. It does so by exchanging the solutions
/// found between explorers so that the explorers almost always with the latest approximate Pareto set
/// update between themselves.
#[derive(Clone)]
pub struct CombinedExplorerIterator2 {
    iterators: Vec<Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>>>,
    is_exact: Vec<bool>,
    duration_left: Option<Duration>,
    solutions_left: Option<u64>,
}

impl CombinedExplorerIterator2 {
    pub fn create(
        explorers_and_models: &[(Arc<dyn Explorer>, Arc<dyn DecisionModel>)],
        biddings: &[ExplorationBid],
        solutions: &HashSet<ExplorationSolution>,
        exploration_configuration: &ExplorationConfiguration,
        solutions_found: u64,
    ) -> Self {
        let new_duration = if exploration_configuration.total_timeout > 0 {
            Some(Duration::from_secs(
                exploration_configuration.total_timeout - Instant::now().elapsed().as_secs(),
            ))
        } else {
            None
        };
        let new_solution_limit = if exploration_configuration.max_sols >= 0 {
            if exploration_configuration.max_sols as u64 > solutions_found {
                Some(exploration_configuration.max_sols as u64 - solutions_found)
            } else {
                Some(0)
            }
        } else {
            None
        };
        CombinedExplorerIterator2 {
            iterators: explorers_and_models
                .iter()
                .map(|(e, m)| e.explore(m.to_owned(), solutions, exploration_configuration.clone()))
                .collect(),
            is_exact: biddings.iter().map(|b| b.is_exact).collect(),
            duration_left: new_duration,
            solutions_left: new_solution_limit,
        }
    }
}

impl Iterator for CombinedExplorerIterator2 {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        let start = Instant::now();
        self.duration_left = self.duration_left.map(|d| {
            if d >= start.elapsed() {
                d - start.elapsed()
            } else {
                Duration::ZERO
            }
        });
        if self.solutions_left.map(|x| x > 0).unwrap_or(true)
            && self
                .duration_left
                .map(|d| d > Duration::ZERO)
                .unwrap_or(true)
        {
            return self
                .iterators
                .par_iter_mut()
                .enumerate()
                .map(|(i, iter_mutex)| {
                    if let Ok(mut iter) = iter_mutex.lock() {
                        return (i, iter.next());
                    }
                    (i, None)
                })
                .take_any_while(|(i, x)| x.is_some() || !self.is_exact[*i])
                .flat_map(|(_, x)| x)
                .find_any(|_| true);
        }
        None
    }
}

pub struct MultiLevelCombinedExplorerIterator2 {
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    biddings: Vec<ExplorationBid>,
    exploration_configuration: ExplorationConfiguration,
    // levels: Vec<CombinedExplorerIterator>,
    // levels_tuple: (Option<CombinedExplorerIterator>, CombinedExplorerIterator),
    iterators: VecDeque<CombinedExplorerIterator2>,
    solutions: HashSet<ExplorationSolution>,
    num_found: u64,
    // converged_to_last_level: bool,
    start: Instant,
}

impl Iterator for MultiLevelCombinedExplorerIterator2 {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        if self.exploration_configuration.total_timeout > 0
            && self.start.elapsed()
                > Duration::from_secs(self.exploration_configuration.total_timeout)
        {
            return None;
        }
        if self.iterators.len() > 2 {
            self.iterators.pop_back();
        }
        if let Some(current_level) = self.iterators.front_mut() {
            if let Some(non_dominated) = current_level.find(|x| {
                !self
                    .solutions
                    .iter()
                    .any(|s| s.partial_cmp(&x) == Some(Ordering::Less))
            }) {
                self.num_found += 1;
                self.solutions.insert(non_dominated.clone());
                let sol_dominates = self
                    .solutions
                    .iter()
                    .any(|cur_sol| non_dominated.partial_cmp(cur_sol) == Some(Ordering::Less));
                if sol_dominates {
                    self.solutions.retain(|cur_sol| {
                        non_dominated.partial_cmp(cur_sol) != Some(Ordering::Less)
                    });
                    let mut new_iterator = CombinedExplorerIterator2::create(
                        self.explorers_and_models.as_slice(),
                        self.biddings.as_slice(),
                        &self.solutions,
                        &self.exploration_configuration,
                        self.num_found,
                    );
                    self.iterators.push_front(new_iterator);
                };
                return Some(non_dominated);
            } else {
                self.iterators.pop_front();
                return self.next();
            }
        };
        None
    }
}

pub fn compute_pareto_solutions(sols: Vec<ExplorationSolution>) -> Vec<ExplorationSolution> {
    sols.iter()
        .filter(|x| {
            !sols
                .iter()
                .filter(|y| x != y)
                .any(|y| y.partial_cmp(x) == Some(Ordering::Less))
        })
        .map(|x| x.to_owned())
        .collect()
}

pub fn explore_cooperatively(
    explorers_and_models: &[(Arc<dyn Explorer>, Arc<dyn DecisionModel>)],
    biddings: &[ExplorationBid],
    currrent_solutions: &HashSet<ExplorationSolution>,
    exploration_configuration: &ExplorationConfiguration,
    // solution_inspector: F,
) -> MultiLevelCombinedExplorerIterator2 {
    let combined_explorer = CombinedExplorerIterator2::create(
        explorers_and_models,
        biddings,
        currrent_solutions,
        exploration_configuration,
        0,
    );
    let mut deque = VecDeque::new();
    deque.push_front(combined_explorer);
    MultiLevelCombinedExplorerIterator2 {
        explorers_and_models: explorers_and_models
            .iter()
            .map(|(e, m)| (e.to_owned(), m.to_owned()))
            .collect(),
        biddings: biddings.to_owned(),
        solutions: currrent_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        iterators: deque,
        start: Instant::now(),
        num_found: 0,
    }
}
