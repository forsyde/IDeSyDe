use std::{
    cmp::Ordering,
    collections::{HashMap, HashSet},
    io::BufRead,
    io::BufReader,
    net::{IpAddr, Ipv4Addr},
    path::PathBuf,
    process::{Child, Stdio},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use derive_builder::Builder;
use idesyde_blueprints::ExplorationSolutionMessage;
use idesyde_core::{
    explore_non_blocking, headers::ExplorationBid, pareto_dominance_partial_cmp, DecisionModel,
    ExplorationConfiguration, ExplorationConfigurationBuilder, ExplorationModule,
    ExplorationSolution, Explorer, OpaqueDecisionModel,
};
use log::{debug, warn};
use reqwest_eventsource::EventSource;
use serde::{Deserialize, Serialize};
use url::Url;

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
    pub conn: tungstenite::WebSocket<std::net::TcpStream>,
}

impl ExternalExplorerSolutionIter {
    /// This creation function assumes that the channel is already sending back solutions
    pub fn new<I>(c: I) -> ExternalExplorerSolutionIter
    where
        I: Into<tungstenite::WebSocket<std::net::TcpStream>>,
    {
        return ExternalExplorerSolutionIter { conn: c.into() };
    }
}

impl Iterator for ExternalExplorerSolutionIter {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        if self.conn.can_read() {
            // while let is here in order to discard keep
            while let Ok(message) = self.conn.read() {
                if message.is_ping() {
                    self.conn
                        .send(tungstenite::Message::Pong(vec![]))
                        .expect("Failed to seng pong message to ping.");
                } else if message.is_text() {
                    if let Some(sol) = ExplorationSolutionMessage::from_json_str(
                        message.into_text().unwrap().as_str(),
                    ) {
                        return Some((
                            Arc::new(OpaqueDecisionModel::from(&sol)) as Arc<dyn DecisionModel>,
                            sol.objectives,
                        ));
                    }
                }
            }
        }
        None
    }
}

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

    fn bid(&self, explorers: &Vec<Arc<dyn Explorer>>, m: Arc<dyn DecisionModel>) -> ExplorationBid {
        let mut form = reqwest::blocking::multipart::Form::new();
        form = form.part(
            format!("decisionModel"),
            reqwest::blocking::multipart::Part::text(
                OpaqueDecisionModel::from(m)
                    .to_json()
                    .expect("Failed to make Json out of opaque decision model. Should never fail."),
            ),
        );
        if let Ok(bid_url) = self
            .url
            .join(format!("/{}", self.name).as_str())
            .and_then(|u| u.join("/bid"))
        {
            match self.client.get(bid_url).multipart(form).send() {
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
        ExplorationBid::impossible(&self.unique_identifier())
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        currrent_solutions: &HashSet<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
    ) -> Box<dyn Iterator<Item = ExplorationSolution> + Send + Sync + '_> {
        if let Ok(explore_url) = self
            .url
            .join(format!("/{}/explore", self.name).as_str())
            .and_then(|u| u.join("/explore"))
        {
            let mut form = reqwest::blocking::multipart::Form::new();
            form = form.text(
                "decisionModel",
                OpaqueDecisionModel::from(m)
                    .to_json()
                    .expect("Failed to make Json out of opaque decision model. Should never fail."),
            );
            form = form.text("configuration", exploration_configuration.to_json_string());
            for (i, sol) in currrent_solutions.iter().enumerate() {
                form = form.text(
                    format!("previousSolution{}", i),
                    ExplorationSolutionMessage::from(sol).to_json_str(),
                );
            }
            if self
                .client
                .post(explore_url)
                .multipart(form)
                .query(&[("session", "0")])
                .send()
                .is_ok_and(|r| r.status().is_success())
            {
                if let Ok(explored_url) = self.url.join(format!("/{}/explored", self.name).as_str())
                {
                }
            } else {
                Box::new(std::iter::empty())
            }
        }
        match std::net::TcpStream::connect(format!("{}:{}", self.address, self.port).as_str()) {
            Ok(stream) => {
                stream
                    .set_read_timeout(None)
                    .expect("Failed to set read timeout to infinite. Should never fail.");
                stream
                    .set_write_timeout(None)
                    .expect("Failed to set write timeout to infinite. Should never fail.");
                match tungstenite::client(
                    format!(
                        "ws://{}:{}/{}/explore",
                        self.address,
                        self.port,
                        self.unique_identifier()
                    )
                    .as_str(),
                    stream,
                ) {
                    Ok((mut conn, _)) => {
                        for sol in &currrent_solutions {
                            if let Err(e) = conn.send(tungstenite::Message::text(
                                ExplorationSolutionMessage::from(sol).to_json_str(),
                            )) {
                                warn!("Failed to send previous solution to explorer {}. Trying to continue anyway.", self.unique_identifier());
                                debug!("Sending error: {}", e.to_string());
                            };
                        }
                        if let Err(e) = conn.send(tungstenite::Message::text(
                            exploration_configuration.to_json_string(),
                        )) {
                            warn!("Failed to send exploration configuration to explorer {}. Trying to continue anyway.", self.unique_identifier());
                            debug!("Sending error: {}", e.to_string());
                        }
                        if let Ok(_) = conn.send(tungstenite::Message::text(
                            DecisionModelMessage::from(m).to_json_str(),
                        )) {
                            return Box::new(ExternalExplorerSolutionIter::new(conn));
                        }
                    }
                    Err(e) => {
                        debug!(
                            "Failed to handshake with {}: {}",
                            self.unique_identifier(),
                            e.to_string()
                        );
                    }
                }
            }
            Err(e) => {
                debug!(
                    "Failed to create connection with {}: {}",
                    self.unique_identifier(),
                    e.to_string()
                );
            }
        }
        Box::new(std::iter::empty())
    }
}

pub struct ExternalServerExplorationModule {
    name: String,
    address: std::net::IpAddr,
    port: usize,
    client: Arc<reqwest::blocking::Client>,
    process: Option<Arc<Mutex<Child>>>,
}

impl ExternalServerExplorationModule {
    pub fn try_create_local(command_path_: PathBuf) -> Option<ExternalServerExplorationModule> {
        // first check if it is indeed a serve-able module
        let is_java = command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let child_res = match is_java {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(&command_path_)
                .arg("--server")
                .arg("http")
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
            false => std::process::Command::new(&command_path_)
                .arg("--server")
                .arg("http")
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
        };
        // the test involves just exitting it
        if let Ok(mut server_child) = child_res {
            if let Some(childout) = &mut server_child.stdout {
                let buf = BufReader::new(childout);
                let port_opt = buf
                    .lines()
                    .flatten()
                    .filter(|l| l.starts_with("INITIALIZED"))
                    .map(|l| l[11..].trim().parse::<usize>())
                    .flatten()
                    .next();
                if let Some(port) = port_opt {
                    return Some(ExternalServerExplorationModule {
                        name: command_path_
                            .clone()
                            .file_name()
                            .and_then(|x| x.to_str())
                            .and_then(|x| x.split('.').next())
                            .expect("Could not fetch name from imodule file name.")
                            .to_string(),
                        address: std::net::IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1)),
                        port: port,
                        client: Arc::new(reqwest::blocking::Client::new()),
                        process: Some(Arc::new(Mutex::new(server_child))),
                    });
                }
            }
        }
        None
    }

    pub fn from(name: &str, ipv4: &Ipv4Addr, port: usize) -> ExternalServerExplorationModule {
        ExternalServerExplorationModule {
            name: name.to_string(),
            address: std::net::IpAddr::V4(ipv4.to_owned()),
            port: port,
            client: Arc::new(reqwest::blocking::Client::new()),
            process: None,
        }
    }
}

impl Drop for ExternalServerExplorationModule {
    fn drop(&mut self) {
        if let Some(server) = &self.process {
            if let Ok(mut server_guard) = server.lock() {
                if let Err(e) = server_guard.kill() {
                    debug!(
                        "Ignoring error whilst killing imodule {}: {}",
                        self.unique_identifier(),
                        e.to_string(),
                    );
                }
            }
        }
    }
}

impl ExplorationModule for ExternalServerExplorationModule {
    fn unique_identifier(&self) -> String {
        self.name.to_owned()
    }

    fn location_url(&self) -> IpAddr {
        self.address
    }

    fn location_port(&self) -> usize {
        self.port
    }

    fn explorers(&self) -> Vec<Arc<dyn Explorer>> {
        match self
            .client
            .get(format!(
                "http://{}:{}/explorers",
                self.location_url(),
                self.location_port()
            ))
            .send()
        {
            Ok(result) => match result.text() {
                Ok(text) => match serde_json::from_str::<Vec<String>>(&text) {
                    Ok(names) => names
                        .iter()
                        .map(|name| {
                            Arc::new(ExternalExplorer {
                                name: name.to_owned(),
                                address: self.address,
                                port: self.port,
                                client: self.client.clone(),
                            })
                        })
                        .map(|x| x as Arc<dyn Explorer>)
                        .collect(),
                    Err(_) => {
                        warn!(
                            "Explorer {} failed deserialize its decision model. Trying to proceed anyway.",
                            self.unique_identifier()
                        );
                        debug!(
                            "Explorer {} failed to deserialize explorer names from {}",
                            self.unique_identifier(),
                            &text
                        );
                        vec![]
                    }
                },
                Err(err) => {
                    warn!(
                        "Explorer {} failed to process request. Trying to proceed anyway.",
                        self.unique_identifier()
                    );
                    debug!(
                        "Explorer {} failed to transform into text with: {}",
                        self.unique_identifier(),
                        err.to_string()
                    );
                    vec![]
                }
            },
            Err(err) => {
                warn!(
                    "Explorer {} failed to accept request. Trying to proceed anyway.",
                    self.unique_identifier()
                );
                debug!(
                    "Explorer {} failed to request with: {}",
                    self.unique_identifier(),
                    err.to_string()
                );
                vec![]
            }
        }
    }
}

pub fn compute_pareto_solutions(sols: Vec<ExplorationSolution>) -> Vec<ExplorationSolution> {
    sols.iter()
        .filter(|x @ (_, objs)| {
            sols.iter()
                .filter(|y| x != y)
                .all(|(_, other_objs)| objs.iter().any(|(k, v)| v < other_objs.get(k).unwrap()))
        })
        .map(|x| x.to_owned())
        .collect()
}

pub struct CombinedExplorerIterator {
    sol_channels: Vec<std::sync::mpsc::Receiver<ExplorationSolution>>,
    is_exact: Vec<bool>,
    finish_request_channels: Vec<std::sync::mpsc::Sender<bool>>,
    duration_left: Option<Duration>,
    _handles: Vec<std::thread::JoinHandle<()>>,
}

impl CombinedExplorerIterator {
    pub fn start(
        explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
        currrent_solutions: Vec<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
    ) -> CombinedExplorerIterator {
        let all_heuristic = explorers_and_models.iter().map(|_| false).collect();
        CombinedExplorerIterator::start_with_exact(
            explorers_and_models,
            all_heuristic,
            currrent_solutions,
            exploration_configuration,
        )
    }

    pub fn start_with_exact(
        explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
        is_exact: Vec<bool>,
        currrent_solutions: Vec<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
    ) -> CombinedExplorerIterator {
        let mut sol_channels: Vec<std::sync::mpsc::Receiver<ExplorationSolution>> = Vec::new();
        let mut completed_channels: Vec<std::sync::mpsc::Sender<bool>> = Vec::new();
        let mut handles: Vec<std::thread::JoinHandle<()>> = Vec::new();
        for (e, m) in &explorers_and_models {
            let (sc, cc, h) = explore_non_blocking(
                e,
                m,
                currrent_solutions.to_owned(),
                exploration_configuration.to_owned(),
            );
            sol_channels.push(sc);
            completed_channels.push(cc);
            handles.push(h);
        }
        CombinedExplorerIterator {
            sol_channels,
            is_exact,
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
                    Ok((explored_decision_model, sol_objs)) => {
                        // debug!("New solution from explorer index {}", i);
                        self.duration_left = self.duration_left.map(|d| {
                            if d >= start.elapsed() {
                                d - start.elapsed()
                            } else {
                                Duration::ZERO
                            }
                        });
                        return Some((explored_decision_model, sol_objs));
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
    levels: Vec<CombinedExplorerIterator>,
    solutions: Vec<ExplorationSolution>,
    converged_to_last_level: bool,
}

impl Iterator for MultiLevelCombinedExplorerIterator {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        match self.levels.last_mut() {
            Some(last_level) => {
                match last_level
                    .filter(|(_, sol_objs)| {
                        // solution is not dominated
                        !self.solutions.iter().any(|(_, y)| {
                            pareto_dominance_partial_cmp(sol_objs, y) == Some(Ordering::Greater)
                        })
                    })
                    .find(|x| !self.solutions.contains(x))
                {
                    Some(solution) => {
                        self.solutions.push(solution.clone());
                        if !self.converged_to_last_level {
                            let (_, sol_objs) = &solution;
                            let sol_dominates = self.solutions.iter().any(|(_, y)| {
                                pareto_dominance_partial_cmp(sol_objs, y) == Some(Ordering::Less)
                            });
                            if sol_dominates {
                                // debug!("Starting new level");
                                self.solutions.retain(|(_, y)| {
                                    pareto_dominance_partial_cmp(&solution.1, y)
                                        != Some(Ordering::Less)
                                });
                                self.levels.push(CombinedExplorerIterator::start(
                                    self.explorers_and_models.clone(),
                                    self.solutions.clone(),
                                    self.exploration_configuration.clone(),
                                ));
                            }
                            if self.levels.len() > 2 {
                                self.levels.remove(0);
                            }
                        }
                        // debug!("solutions {}", self.solutions.len());
                        return Some(solution);
                        // self.previous = Some(self.current_level);
                        // self.current_level
                    }
                    None => {
                        if !self.converged_to_last_level {
                            self.converged_to_last_level = true;
                            self.levels.remove(self.levels.len() - 1);
                            return self.next();
                        }
                    }
                }
            }
            None => {}
        };
        None
    }
}

pub fn explore_cooperatively_simple(
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    currrent_solutions: Vec<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    // solution_inspector: F,
) -> MultiLevelCombinedExplorerIterator {
    MultiLevelCombinedExplorerIterator {
        explorers_and_models: explorers_and_models.clone(),
        solutions: currrent_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        levels: vec![CombinedExplorerIterator::start(
            explorers_and_models,
            currrent_solutions,
            exploration_configuration.to_owned(),
        )],
        converged_to_last_level: false,
    }
}

pub fn explore_cooperatively(
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    biddings: Vec<ExplorationBid>,
    currrent_solutions: Vec<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    // solution_inspector: F,
) -> MultiLevelCombinedExplorerIterator {
    MultiLevelCombinedExplorerIterator {
        explorers_and_models: explorers_and_models.clone(),
        solutions: currrent_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        levels: vec![CombinedExplorerIterator::start_with_exact(
            explorers_and_models,
            biddings.iter().map(|b| b.is_exact).collect(),
            currrent_solutions,
            exploration_configuration.to_owned(),
        )],
        converged_to_last_level: false,
    }
}
