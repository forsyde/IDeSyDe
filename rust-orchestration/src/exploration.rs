use std::{
    cmp::Ordering,
    collections::HashMap,
    io::BufRead,
    io::BufReader,
    net::Ipv4Addr,
    path::PathBuf,
    process::{Child, Stdio},
    sync::{Arc, Mutex},
};

use idesyde_blueprints::{DecisionModelMessage, ExplorationSolutionMessage, OpaqueDecisionModel};
use idesyde_core::{
    headers::ExplorationBid, DecisionModel, ExplorationConfiguration, ExplorationModule,
    ExplorationSolution,
};
use log::{debug, warn};
use serde::{Deserialize, Serialize};

use crate::HttpServerLike;

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
    explorer_id: String,
    model_message: DecisionModelMessage,
    previous_solutions: Vec<ExplorationSolutionMessage>,
}

impl ExplorationRequestMessage {
    pub fn from<T: DecisionModel + ?Sized>(
        explorer_name: &str,
        decision_model: &T,
    ) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            explorer_id: explorer_name.to_owned(),
            model_message: DecisionModelMessage::from(decision_model),
            previous_solutions: vec![],
        }
    }

    pub fn with_previous_solutions<T: DecisionModel + ?Sized>(
        explorer_name: &str,
        decision_model: &T,
        previous_solutions: Vec<ExplorationSolutionMessage>,
    ) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            explorer_id: explorer_name.to_owned(),
            model_message: DecisionModelMessage::from(decision_model),
            previous_solutions: previous_solutions,
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

impl HttpServerLike for ExternalServerExplorationModule {
    fn get_client(&self) -> Arc<reqwest::blocking::Client> {
        self.client.clone()
    }

    fn get_address(&self) -> std::net::IpAddr {
        self.address.clone()
    }

    fn get_port(&self) -> usize {
        self.port
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

pub struct HttpServerSolutionIter {
    pub conn: tungstenite::WebSocket<std::net::TcpStream>,
}

impl HttpServerSolutionIter {
    /// This creation function assumes that the channel is already sending back solutions
    pub fn new<I>(c: I) -> HttpServerSolutionIter
    where
        I: Into<tungstenite::WebSocket<std::net::TcpStream>>,
    {
        return HttpServerSolutionIter { conn: c.into() };
    }
}

impl Iterator for HttpServerSolutionIter {
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

impl ExplorationModule for ExternalServerExplorationModule {
    fn unique_identifier(&self) -> String {
        self.name.to_owned()
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<ExplorationBid> {
        let message = DecisionModelMessage::from_dyn_decision_model(m.as_ref());
        if let Ok(res) = self.http_get("bid", &vec![], &message) {
            if let Some(bids) = res
                .text()
                .ok()
                .and_then(|x| serde_json::from_str::<Vec<ExplorationBid>>(&x).ok())
            {
                return bids;
            }
        }
        Vec::new()
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        currrent_solutions: Vec<ExplorationSolution>,
        exploration_configuration: ExplorationConfiguration,
    ) -> Box<dyn Iterator<Item = ExplorationSolution> + '_> {
        if let Err(e) = self
            .http_post(
                "set",
                &vec![("parameter", "max-sols")],
                &exploration_configuration.max_sols,
            )
            .and(self.http_post(
                "set",
                &vec![("parameter", "total-timeout")],
                &exploration_configuration.total_timeout,
            ))
            .and(self.http_post(
                "set",
                &vec![("parameter", "time-resolution")],
                &exploration_configuration.time_resolution,
            ))
            .and(self.http_post(
                "set",
                &vec![("parameter", "memory-resolution")],
                &exploration_configuration.memory_resolution,
            ))
        {
            warn!(
                "Could not set exploration parameters for module {}. Trying to continue.",
                self.unique_identifier()
            );
            debug!("Error is {}", e.to_string());
        };
        match std::net::TcpStream::connect(format!("{}:{}", self.address, self.port).as_str()) {
            Ok(stream) => {
                stream
                    .set_read_timeout(None)
                    .expect("Failed to set read timeout to infinite. Should never fail.");
                stream
                    .set_write_timeout(None)
                    .expect("Failed to set write timeout to infinite. Should never fail.");
                match tungstenite::client(
                    format!("ws://{}:{}/explore", self.address, self.port).as_str(),
                    stream,
                ) {
                    Ok((mut conn, _)) => {
                        let message = ExplorationRequestMessage::with_previous_solutions(
                            explorer_id,
                            m.as_ref(),
                            currrent_solutions
                                .iter()
                                .map(|x| ExplorationSolutionMessage::from(x))
                                .collect(),
                        );
                        if let Ok(_) = conn.send(tungstenite::Message::text(message.to_json_str()))
                        {
                            return Box::new(HttpServerSolutionIter::new(conn));
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

pub fn explore_cooperatively(
    m: Arc<dyn DecisionModel>,
    emodules_and_explorer_ids: Vec<(Arc<dyn ExplorationModule>, &str)>,
    currrent_solutions: Vec<ExplorationSolution>,
    exploration_configuration: ExplorationConfiguration,
    // solution_inspector: F,
) -> Vec<ExplorationSolution>
// where
//     F: Fn(ExplorationSolution) -> () + Copy + Send,
{
    let mut aggregated_solutions = Vec::new();
    aggregated_solutions.extend(currrent_solutions.into_iter());
    let shared_solutions = Arc::new(Mutex::new(aggregated_solutions));
    let completed = Arc::new(Mutex::new(false));
    rayon::scope(|s| {
        // the explorers
        for (emodule, explorer_id) in emodules_and_explorer_ids {
            let m_cloned = m.clone();
            let completed_cloned = completed.clone();
            let shared_solutions_cloned = shared_solutions.clone();
            s.spawn(move |_| {
                while let Ok(false) = completed_cloned.lock().map(|x| *x) {
                    let previous_solutions = shared_solutions_cloned
                        .lock()
                        .map(|x| x.clone())
                        .unwrap_or(Vec::new());
                    let mut should_restart = false;
                    for (solved_model, sol_objs) in emodule.explore(
                        m_cloned.to_owned(),
                        explorer_id,
                        previous_solutions,
                        exploration_configuration.to_owned(),
                    ) {
                        if let Ok(mut sols) = shared_solutions_cloned.lock() {
                            // no other solution dominates the found one
                            let sol_not_dominated = sols.len() == 0
                                || sols.iter().any(|(_, y)| {
                                    pareto_dominance_partial_cmp(&sol_objs, y)
                                        != Some(Ordering::Greater)
                                });
                            if sol_not_dominated {
                                // debug info
                                debug!(
                                    "Found a new solution with objectives: {}.",
                                    &sol_objs
                                        .iter()
                                        .map(|(k, v)| format!("{}: {}", k, v))
                                        .reduce(|s1, s2| format!("{}, {}", s1, s2))
                                        .unwrap_or("None".to_owned())
                                );
                                // solution_inspector((solved_model, sol_objs));
                                // then it is an improvement, take out the solutions that are dominated
                                sols.retain(|(_, y)| {
                                    pareto_dominance_partial_cmp(&sol_objs, y)
                                        != Some(Ordering::Less)
                                });
                                //add the new solution
                                sols.push((solved_model, sol_objs));
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
            });
        }
        // let explorer_channels: Vec<
        //     std::sync::mpsc::Sender<(Arc<dyn DecisionModel>, Vec<ExplorationSolution>)>,
        // > = emodules_and_explorer_ids
        //     .iter()
        //     .map(|(emodule, e_id)| {
        //         let (exp_tx, exp_rx) =
        //             std::sync::mpsc::channel::<(Arc<dyn DecisionModel>, Vec<ExplorationSolution>)>(
        //             );
        //         s.spawn(move |_| {
        //             while let Ok((in_model, received_sols)) = exp_rx.recv() {
        //                 for sol in emodule.explore(
        //                     in_model,
        //                     *e_id,
        //                     received_sols,
        //                     exploration_configuration.to_owned(),
        //                 ) {
        //                     if exp_rx.
        //                 }
        //             }
        //         });
        //         exp_tx
        //     })
        //     .collect();
        // for (emodule, explorer_id) in emodules_and_explorer_ids {}
        // the main controller for the cooperation
        // s.spawn(move |_| {});
    });
    return shared_solutions.lock().unwrap().to_owned();
}
