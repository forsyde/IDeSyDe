use std::{
    borrow::BorrowMut,
    cmp::Ordering,
    collections::HashMap,
    io::BufRead,
    io::BufReader,
    net::{IpAddr, Ipv4Addr},
    path::PathBuf,
    process::{Child, Stdio},
    sync::{
        mpsc::{Receiver, Sender},
        Arc, Mutex,
    },
    thread::JoinHandle,
};

use idesyde_blueprints::{DecisionModelMessage, ExplorationSolutionMessage, OpaqueDecisionModel};
use idesyde_core::{
    headers::ExplorationBid, DecisionModel, ExplorationConfiguration, ExplorationModule,
    ExplorationSolution, Explorer,
};
use log::{debug, warn};
use serde::{Deserialize, Serialize};

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
#[derive(Deserialize, Serialize, PartialEq, Clone)]
pub struct ExplorationRequestMessage {
    model_message: DecisionModelMessage,
    previous_solutions: Vec<ExplorationSolutionMessage>,
    configuration: ExplorationConfiguration,
}

impl ExplorationRequestMessage {
    pub fn from<T: DecisionModel + ?Sized>(decision_model: &T) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            model_message: DecisionModelMessage::from(decision_model),
            previous_solutions: vec![],
            configuration: ExplorationConfiguration::default(),
        }
    }

    pub fn with_previous_solutions<T: DecisionModel + ?Sized>(
        decision_model: &T,
        previous_solutions: Vec<ExplorationSolutionMessage>,
    ) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            model_message: DecisionModelMessage::from(decision_model),
            previous_solutions: previous_solutions,
            configuration: ExplorationConfiguration::default(),
        }
    }

    pub fn complete<T: DecisionModel + ?Sized>(
        decision_model: &T,
        previous_solutions: Vec<ExplorationSolutionMessage>,
        configuration: &ExplorationConfiguration,
    ) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            model_message: DecisionModelMessage::from(decision_model),
            previous_solutions: previous_solutions,
            configuration: configuration.to_owned(),
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
    address: std::net::IpAddr,
    port: usize,
    client: Arc<reqwest::blocking::Client>,
}

impl Explorer for ExternalExplorer {
    fn unique_identifier(&self) -> String {
        self.name.to_owned()
    }

    fn location_url(&self) -> IpAddr {
        self.address
    }

    fn location_port(&self) -> usize {
        self.port
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> ExplorationBid {
        match self
            .client
            .get(format!(
                "http://{}:{}/{}/bid",
                self.location_url(),
                self.location_port(),
                self.name
            ))
            .body(DecisionModelMessage::from_dyn_decision_model(m.as_ref()).to_json_str())
            .send()
        {
            Ok(result) => match result.text() {
                Ok(text) => {
                    if !text.is_empty() {
                        match ExplorationBid::from_json_str(&text) {
                            Some(bid) => return bid,
                            None => {
                                debug!(
                                    "Explorer {} failed deserialize message {}",
                                    self.unique_identifier(),
                                    &text
                                );
                            }
                        }
                    }
                    ExplorationBid::impossible(&self.unique_identifier())
                }
                Err(err) => {
                    debug!(
                        "Explorer {} failed to transform into text with: {}",
                        self.unique_identifier(),
                        err.to_string()
                    );
                    ExplorationBid::impossible(&self.unique_identifier())
                }
            },
            Err(err) => {
                debug!(
                    "Explorer {} failed to request with: {}",
                    self.unique_identifier(),
                    err.to_string()
                );
                ExplorationBid::impossible(&self.unique_identifier())
            }
        }
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        currrent_solutions: Vec<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
    ) -> Box<dyn Iterator<Item = ExplorationSolution> + '_> {
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
                        let message = ExplorationRequestMessage::complete(
                            m.as_ref(),
                            currrent_solutions
                                .iter()
                                .map(|x| ExplorationSolutionMessage::from(x))
                                .collect(),
                            &exploration_configuration,
                        );
                        if let Ok(_) = conn.send(tungstenite::Message::text(message.to_json_str()))
                        {
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

pub fn pareto_dominance_partial_cmp(
    lhs: &HashMap<String, f64>,
    rhs: &HashMap<String, f64>,
) -> Option<Ordering> {
    if lhs.keys().all(|x| rhs.contains_key(x)) && rhs.keys().all(|x| lhs.contains_key(x)) {
        if lhs.iter().all(|(k, v)| v == rhs.get(k).unwrap()) {
            Some(Ordering::Equal)
        } else if lhs.iter().all(|(k, v)| v < rhs.get(k).unwrap()) {
            Some(Ordering::Less)
        } else if lhs.iter().all(|(k, v)| v > rhs.get(k).unwrap()) {
            Some(Ordering::Greater)
        } else {
            None
        }
    } else {
        None
    }
}

pub struct CombinedExplorerIterator {
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    currrent_solutions: Arc<Mutex<Vec<ExplorationSolution>>>,
    exploration_configuration: ExplorationConfiguration,
    _last_elapsed_time: u64,
    threads: Option<Vec<JoinHandle<()>>>,
    solutions_rx: Receiver<ExplorationSolution>,
    solutions_tx: Sender<ExplorationSolution>,
    completed: Arc<Mutex<bool>>,
}

impl Iterator for CombinedExplorerIterator {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        // create the threads of necessary
        if let None = self.threads {
            let threads = self
                .explorers_and_models
                .iter()
                .map(|(explorer, m)| {
                    // a copy of all the important thigs to give the thread
                    let this_sol_tx = self.solutions_tx.clone();
                    let this_m = m.clone();
                    let this_currrent_solutions = self.currrent_solutions.clone();
                    let this_exploration_configuration = self.exploration_configuration.clone();
                    let this_explorer = explorer.clone();
                    let completed_cloned = self.completed.clone();
                    // the communiation aspects with the main thread
                    // finally, the thread logic to keep exploring
                    std::thread::spawn(move || {
                        while let Ok(false) = completed_cloned.lock().map(|x| *x) {
                            let previous_solutions = this_currrent_solutions
                                .lock()
                                .map(|x| x.clone())
                                .unwrap_or(Vec::new());
                            let mut should_restart = false;
                            for (solved_model, sol_objs) in this_explorer.explore(
                                this_m.to_owned(),
                                previous_solutions,
                                this_exploration_configuration,
                            ) {
                                if let Ok(mut sols) = this_currrent_solutions.lock() {
                                    // no other solution dominates the found one
                                    let sol_not_dominated = sols.len() == 0
                                        || sols.iter().any(|(_, y)| {
                                            pareto_dominance_partial_cmp(&sol_objs, y)
                                                != Some(Ordering::Greater)
                                        });
                                    if sol_not_dominated {
                                        // solution_inspector((solved_model, sol_objs));
                                        // then it is an improvement, take out the solutions that are dominated
                                        sols.retain(|(_, y)| {
                                            pareto_dominance_partial_cmp(&sol_objs, y)
                                                != Some(Ordering::Less)
                                        });
                                        //add the new solution
                                        sols.push((solved_model.clone(), sol_objs.clone()));
                                        if let Err(e) = this_sol_tx.send((solved_model, sol_objs)) {
                                            debug!("Could not send solution to control thread because {}", e.to_string());
                                        };
                                    } else {
                                        should_restart = true;
                                    }
                                }
                                if should_restart {
                                    break;
                                }
                            }
                            if !should_restart {
                                // if no improvement has been made but the loop finished, mark as complete
                                if let Ok(mut comp) = completed_cloned.lock() {
                                    *comp = true;
                                }
                            }
                        }
                    })
                })
                .collect();
            self.threads = Some(threads);
        }
        // now create the reciever logic
        while !self.completed.lock().map(|x| *x).unwrap_or(false) {
            match self
                .solutions_rx
                .recv_timeout(std::time::Duration::from_millis(300))
            {
                Ok(sol) => return Some(sol),
                Err(_) => {}
            }
        }
        // if it is completed, we can simply iterate the channel until completion
        self.solutions_rx.try_recv().ok()
    }
}

pub fn explore_cooperatively(
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    currrent_solutions: Vec<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    // solution_inspector: F,
) -> CombinedExplorerIterator {
    let (sols_tx, sols_rx) = std::sync::mpsc::channel();
    CombinedExplorerIterator {
        explorers_and_models,
        currrent_solutions: Arc::new(Mutex::new(currrent_solutions)),
        exploration_configuration,
        _last_elapsed_time: 0u64,
        threads: None,
        solutions_rx: sols_rx,
        solutions_tx: sols_tx,
        completed: Arc::new(Mutex::new(false)),
    }
}
