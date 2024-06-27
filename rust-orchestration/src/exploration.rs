use std::{
    cmp::Ordering,
    collections::{HashMap, HashSet, VecDeque},
    sync::{
        mpsc::{Receiver, Sender},
        Arc, Mutex,
    },
    time::{Duration, Instant},
};

use derive_builder::Builder;
use idesyde_blueprints::ExplorationSolutionMessage;
use idesyde_core::{
    DecisionModel, ExplorationBid, ExplorationConfiguration, ExplorationConfigurationBuilder,
    ExplorationSolution, Explorer, OpaqueDecisionModel,
};
use log::{debug, warn};
use reqwest::blocking::multipart::Form;
use serde::{Deserialize, Serialize};
use url::Url;

// use rayon::prelude::*;

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
        serde_json::to_string(self).expect("Failed to serialize but should always succeed")
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
        Arc::new(Mutex::new(std::iter::empty()))
    }
}

#[derive(PartialEq, Eq, Copy, Clone, Debug)]
pub enum ExplorationStatus {
    Optimal,
    Dominated,
    Unknown,
}

pub fn explore_level_non_blocking(
    explorers_and_models: &[(Arc<dyn Explorer>, Arc<dyn DecisionModel>)],
    biddings: &[ExplorationBid],
    configuration: &ExplorationConfiguration,
    solutions: &HashSet<ExplorationSolution>,
) -> (Arc<Mutex<ExplorationStatus>>, Receiver<ExplorationSolution>) {
    let status = Arc::new(Mutex::new(ExplorationStatus::Unknown));
    let (level_tx, level_rx) = std::sync::mpsc::channel::<ExplorationSolution>();
    for ((explorer, model), b) in explorers_and_models.iter().zip(biddings.iter()) {
        let explorer = explorer.clone();
        let model = model.clone();
        let conf = configuration.to_owned();
        // let mut time_out = 1u64;
        // let mut time_out_step = 1u64;
        // while time_out < configuration.improvement_timeout {
        //     configurations.push_back(ExplorationConfiguration {
        //         improvement_timeout: time_out,
        //         target_objectives: configuration.target_objectives.clone(),
        //         ..configuration.clone()
        //     });
        //     // if time_out / time_out_step == 10 {
        //     //     time_out_step *= 10;
        //     // }
        //     time_out_step *= 2;
        //     time_out = time_out + time_out_step;
        // }
        // configurations.push_back(configuration.clone());
        let current_solutions = solutions.clone();
        let level_tx = level_tx.clone();
        let this_status = status.clone();
        let is_exact = b.is_exact;
        let time_out_duration = if configuration.improvement_timeout > 0 {
            Some(Duration::from_secs(configuration.improvement_timeout))
        } else {
            None
        };
        rayon::spawn(move || {
            // if let Some(duration) = time_out_duration {
            //     if Instant::now().elapsed() >= duration {
            //         return;
            //     }
            // }
            // if this_status
            //     .lock()
            //     .map(|x| *x == ExplorationStatus::Dominated || *x == ExplorationStatus::Optimal)
            //     .unwrap_or(true)
            // {
            //     return;
            // }
            // if let Some(conf) = configurations.pop_front() {
            // let tout = conf.improvement_timeout.to_owned();
            let iter_mutex =
                explorer.explore(model.to_owned(), &current_solutions, conf.to_owned());
            let start = Instant::now();
            if let Ok(mut iter) = iter_mutex.lock() {
                while let Some(sol) = iter.next() {
                    if current_solutions
                        .iter()
                        .all(|cur| cur.partial_cmp(&sol) != Some(Ordering::Less))
                        && !current_solutions.contains(&sol)
                    {
                        match level_tx.send(sol) {
                            Ok(_) => (),
                            Err(_) => return,
                        }
                    }
                    if let Some(duration) = time_out_duration {
                        if start.elapsed() >= duration {
                            return;
                        }
                    }
                    if this_status
                        .lock()
                        .map(|x| {
                            *x == ExplorationStatus::Dominated || *x == ExplorationStatus::Optimal
                        })
                        .unwrap_or(true)
                    {
                        return;
                    }
                }
                if this_status
                    .lock()
                    .map(|x| *x != ExplorationStatus::Dominated)
                    .unwrap_or(false)
                    && is_exact
                    && time_out_duration
                        .map(|improv_tout| improv_tout > start.elapsed())
                        .unwrap_or(true)
                {
                    // debug!(
                    //     "Explorer {} proven it is optimal",
                    //     explorer.unique_identifier()
                    // );
                    let _ = this_status
                        .lock()
                        .map(|mut x| *x = ExplorationStatus::Optimal);
                    return;
                }
            };
            // } else {
            //     return;
            // }
        });
    }
    (status, level_rx)
}

pub struct MultiLevelCombinedExplorerIterator3 {
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    biddings: Vec<ExplorationBid>,
    exploration_configuration: ExplorationConfiguration,
    // levels: Vec<CombinedExplorerIterator>,
    // levels_tuple: (Option<CombinedExplorerIterator>, CombinedExplorerIterator),
    current_solutions: HashSet<ExplorationSolution>,
    levels_streams: Vec<Receiver<ExplorationSolution>>,
    levels_status: Vec<Arc<Mutex<ExplorationStatus>>>,
    levels_is_exact: Vec<bool>,
    levels_start: Vec<Instant>,
    num_found: u64,
    // converged_to_last_level: bool,
    start: Instant,
}

impl Iterator for MultiLevelCombinedExplorerIterator3 {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        loop {
            if self.exploration_configuration.total_timeout > 0
                && self.start.elapsed()
                    > Duration::from_secs(self.exploration_configuration.total_timeout)
            {
                return None;
            }
            if self.levels_streams.len() == 0 {
                return None;
            }
            while self.levels_streams.len() > 2 {
                let _ = self.levels_status[0]
                    .lock()
                    .map(|mut x| *x = ExplorationStatus::Dominated);
                self.levels_status.remove(0);
                self.levels_streams.remove(0);
                self.levels_start.remove(0);
                self.levels_is_exact.remove(0);
            }
            for i in (0..self.levels_streams.len()).rev() {
                if let Some(level) = self.levels_streams.get(i) {
                    match level.recv_timeout(Duration::from_millis(500)) {
                        Ok(solution) => {
                            if !self.current_solutions.iter().any(|s| {
                                s.partial_cmp(&solution) == Some(Ordering::Less)
                                    || s.partial_cmp(&solution) == Some(Ordering::Equal)
                            }) {
                                self.num_found += 1;
                                // let sol_dominates = self.current_solutions.is_empty()
                                //     || self.current_solutions.iter().any(|cur_sol| {
                                //         solution.partial_cmp(cur_sol) == Some(Ordering::Less)
                                //     });
                                self.current_solutions.insert(solution.clone());
                                self.current_solutions.retain(|cur_sol| {
                                    solution.partial_cmp(cur_sol) != Some(Ordering::Less)
                                });
                                // println!("New level");
                                let (is_dominated, new_level) = explore_level_non_blocking(
                                    &self.explorers_and_models,
                                    self.biddings.as_slice(),
                                    &self.exploration_configuration,
                                    &self.current_solutions,
                                );
                                self.levels_streams.push(new_level);
                                self.levels_status.push(is_dominated);
                                self.levels_start.push(Instant::now());
                                self.levels_is_exact
                                    .push(self.biddings.iter().any(|x| x.is_exact));
                                // if sol_dominates {
                                // }
                                return Some(solution);
                            }
                        }
                        Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                            let improv_timed_out =
                                if self.exploration_configuration.improvement_timeout > 0 {
                                    self.levels_start[i].elapsed().as_secs()
                                        >= self.exploration_configuration.improvement_timeout
                                } else {
                                    false
                                };
                            if improv_timed_out {
                                self.levels_streams.remove(i);
                                self.levels_status.remove(i);
                                self.levels_start.remove(i);
                                self.levels_is_exact.remove(i);
                                break;
                            }
                        }
                        Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                            // let optimal = self.levels_status[i]
                            // .lock()
                            // .map(|x| *x == ExplorationStatus::Optimal)
                            // .unwrap_or(false);
                            // let all_optimal_finished = (0..self.levels_streams.len())
                            // .filter(|i| self.levels_is_exact[*i]).all(|i| 
                            //     self.levels_status[i]
                            //         .lock()
                            //         .map(|x| *x == ExplorationStatus::Optimal)
                            //         .unwrap_or(false)
                            
                            // );
                            // println!("Disconnected level with left: {}, {}", all_optimal_finished, self.levels_streams.len());
                            self.levels_streams.remove(i);
                            self.levels_status.remove(i);
                            self.levels_start.remove(i);
                            self.levels_is_exact.remove(i);
                            // if all_optimal_finished {
                            //     return None;
                            // }
                            break;
                        }
                    }
                }
            }
        }
    }
}

/// This iterator is able to get a handful of explorers + decision models combination
/// and make the exploration cooperative. It does so by exchanging the solutions
/// found between explorers so that the explorers almost always with the latest approximate Pareto set
/// update between themselves.
#[derive(Clone)]
pub struct CombinedExplorerIterator2 {
    iterators: Vec<Arc<Mutex<dyn Iterator<Item = ExplorationSolution> + Send + Sync>>>,
    _is_exact: Vec<bool>,
    configuration: ExplorationConfiguration,
    current_solutions: HashSet<ExplorationSolution>,
    start: Instant,
    sol_tx: Arc<Sender<ExplorationSolution>>,
    sol_rx: Arc<Receiver<ExplorationSolution>>,
}

impl CombinedExplorerIterator2 {
    pub fn create(
        explorers_and_models: &[(Arc<dyn Explorer>, Arc<dyn DecisionModel>)],
        biddings: &[ExplorationBid],
        solutions: &HashSet<ExplorationSolution>,
        exploration_configuration: &ExplorationConfiguration,
    ) -> Self {
        // let new_duration = if exploration_configuration.total_timeout > 0 {
        //     Some(Duration::from_secs(
        //         exploration_configuration.total_timeout - Instant::now().elapsed().as_secs(),
        //     ))
        // } else {
        //     None
        // };
        // let new_solution_limit = if exploration_configuration.max_sols >= 0 {
        //     if exploration_configuration.max_sols as u64 > solutions_found {
        //         Some(exploration_configuration.max_sols as u64 - solutions_found)
        //     } else {
        //         Some(0)
        //     }
        // } else {
        //     None
        // };
        let (sol_tx, sol_rx) = std::sync::mpsc::channel::<ExplorationSolution>();
        CombinedExplorerIterator2 {
            iterators: explorers_and_models
                .iter()
                .map(|(e, m)| e.explore(m.to_owned(), solutions, exploration_configuration.clone()))
                .collect(),
            _is_exact: biddings.iter().map(|b| b.is_exact).collect(),
            configuration: exploration_configuration.to_owned(),
            current_solutions: solutions.to_owned(),
            start: Instant::now(),
            sol_tx: Arc::new(sol_tx),
            sol_rx: Arc::new(sol_rx),
        }
    }
}

impl Iterator for CombinedExplorerIterator2 {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        if self.configuration.improvement_timeout > 0
            && self.start.elapsed() > Duration::from_secs(self.configuration.improvement_timeout)
        {
            return None;
        }
        for iter_mutex_ref in &self.iterators {
            let iter_mutex = iter_mutex_ref.to_owned();
            let sol_tx = self.sol_tx.clone();
            let current_solutions = self.current_solutions.clone();
            rayon::spawn(move || {
                if let Ok(mut iter) = iter_mutex.lock() {
                    while let Some(sol) = iter.next() {
                        if current_solutions
                            .iter()
                            .all(|cur| cur.partial_cmp(&sol) != Some(Ordering::Less))
                            && !current_solutions.contains(&sol)
                        {
                            let _ = sol_tx.send(sol);
                        }
                    }
                }
            });
        }
        if self.configuration.improvement_timeout > 0 {
            self.sol_rx
                .recv_timeout(Duration::from_secs(self.configuration.improvement_timeout))
                .ok()
        } else {
            self.sol_rx.recv().ok()
        }
        // return self
        //     .iterators
        //     .par_iter_mut()
        //     .enumerate()
        //     .find_map_any(|(_, iter_mutex)| {
        //         if let Ok(mut iter) = iter_mutex.lock() {
        //             while let Some(sol) = iter.next() {
        //                 if self.current_solutions.iter().all(|cur| cur.partial_cmp(&sol) != Some(Ordering::Less)) && !self.current_solutions.contains(&sol) {
        //                     return Some(sol);
        //                 }
        //             }
        //         }
        //         None
        //     });
        // .take_any_while(|(i, x)| {
        //     x.is_some() || self.is_exact[*i]
        // })
        // .flat_map(|(_, x)| x)
        // .find_any(|_| true);
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
                    let new_iterator = CombinedExplorerIterator2::create(
                        self.explorers_and_models.as_slice(),
                        self.biddings.as_slice(),
                        &self.solutions,
                        &self.exploration_configuration,
                    );
                    self.iterators.push_front(new_iterator);
                };
                return Some(non_dominated);
            } else {
                self.iterators.pop_front();
                // restart improvement time out of previous level
                if let Some(prev_level) = self.iterators.front_mut() {
                    prev_level.start = Instant::now();
                }
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
    current_solutions: &HashSet<ExplorationSolution>,
    exploration_configuration: &ExplorationConfiguration,
    // solution_inspector: F,
) -> MultiLevelCombinedExplorerIterator3 {
    let (is_dominated, new_level) = explore_level_non_blocking(
        explorers_and_models,
        biddings,
        exploration_configuration,
        current_solutions,
    );
    // let combined_explorer = CombinedExplorerIterator2::create(
    //     explorers_and_models,
    //     biddings,
    //     currrent_solutions,
    //     exploration_configuration,
    // );
    // let mut deque = VecDeque::new();
    // deque.push_front(combined_explorer);
    MultiLevelCombinedExplorerIterator3 {
        explorers_and_models: Vec::from(explorers_and_models),
        biddings: biddings.to_owned(),
        current_solutions: current_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        start: Instant::now(),
        num_found: 0,
        levels_streams: vec![new_level],
        levels_status: vec![is_dominated],
        levels_start: vec![Instant::now()],
        levels_is_exact: vec![biddings.iter().any(|x| x.is_exact)],
    }
}
