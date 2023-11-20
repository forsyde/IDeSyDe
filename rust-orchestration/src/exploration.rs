use std::{
    cmp::Ordering,
    collections::{HashMap, HashSet},
    io::BufRead,
    io::BufReader,
    path::PathBuf,
    process::{Child, Stdio},
    sync::{Arc, Mutex},
};

use derive_builder::Builder;
use idesyde_blueprints::ExplorationSolutionMessage;
use idesyde_core::{
    DecisionModel, ExplorationBid, ExplorationConfiguration, ExplorationConfigurationBuilder,
    ExplorationSolution, Explorer, Module, OpaqueDecisionModel,
};
use log::{debug, warn};
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
                    tungstenite::Message::Text(sol_json) => {
                        if let Some(sol) = ExplorationSolutionMessage::from_json_str(&sol_json) {
                            return Some(ExplorationSolution {
                                solved: Arc::new(OpaqueDecisionModel::from(&sol))
                                    as Arc<dyn DecisionModel>,
                                objectives: sol.objectives,
                            });
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

    fn bid(
        &self,
        _explorers: &Vec<Arc<dyn Explorer>>,
        m: Arc<dyn DecisionModel>,
    ) -> ExplorationBid {
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
        let mut mut_url = self.url.clone();
        if let Err(_) = mut_url.set_scheme("ws") {
            warn!(
                "Failed to set up exploration websocket. Module {} is likely to fail exploration.",
                self.unique_identifier()
            );
        };
        if let Ok(explore_url) = mut_url.join("/explore") {
            if let Some((mut ws, _)) = std::net::TcpStream::connect(mut_url.as_str())
                .ok()
                .and_then(|stream| tungstenite::client(explore_url, stream).ok())
            {
                if let Ok(design_cbor) = OpaqueDecisionModel::from(m).to_cbor() {
                    if let Err(e) = ws.send(tungstenite::Message::Binary(design_cbor)) {
                        warn!("Failed to send decision model to {} for exploration. Trying to proceed anyway.", self.unique_identifier());
                        debug!("Message was: {}", e.to_string());
                    };
                };
                for prev_sol in currrent_solutions {
                    if let Ok(design_cbor) = ExplorationSolutionMessage::from(prev_sol).to_cbor() {
                        if let Err(e) = ws.send(tungstenite::Message::Binary(design_cbor)) {
                            warn!("Failed to send previous solution to {} for exploration. Trying to proceed anyway.", self.unique_identifier());
                            debug!("Message was: {}", e.to_string());
                        };
                    };
                }
                if let Ok(conf_cbor) = exploration_configuration.to_cbor() {
                    if let Err(e) = ws.send(tungstenite::Message::Binary(conf_cbor)) {
                        warn!("Failed to send configuration to {} for exploration. Trying to proceed anyway.", self.unique_identifier());
                        debug!("Message was: {}", e.to_string());
                    };
                }
                if let Err(e) = ws.send(tungstenite::Message::Text("done".to_string())) {
                    warn!("Failed to send exploration request to {} for exploration. Exploration is likely to fail.", self.unique_identifier());
                    debug!("Message was: {}", e.to_string());
                };
                return Box::new(ExternalExplorerSolutionIter::new(ws));
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
        Box::new(std::iter::empty())
    }
}

#[derive(Builder, Clone)]
pub struct ExternalServerExplorationModule {
    name: String,
    url: Url,
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
                        url: url::Url::parse(format!("http://127.0.0.1:{}", port).as_str())
                            .expect("Failed to parse a HTTP url. Should never fail."),
                        client: Arc::new(reqwest::blocking::Client::new()),
                        process: Some(Arc::new(Mutex::new(server_child))),
                    });
                }
            }
        }
        None
    }

    // pub fn from(name: &str, ipv4: &Ipv4Addr, port: usize) -> ExternalServerExplorationModule {
    //     ExternalServerExplorationModule {
    //         name: name.to_string(),
    //         address: std::net::IpAddr::V4(ipv4.to_owned()),
    //         port: port,
    //         client: Arc::new(reqwest::blocking::Client::new()),
    //         process: None,
    //     }
    // }
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

impl Module for ExternalServerExplorationModule {
    fn unique_identifier(&self) -> String {
        self.name.to_owned()
    }

    fn location_url(&self) -> std::option::Option<url::Url> {
        Some(self.url.to_owned())
    }

    fn explorers(&self) -> Vec<Arc<dyn Explorer>> {
        if let Ok(explorers_url) = self.url.join("/explorers") {
            match self.client.get(explorers_url).send() {
                Ok(result) => match result.text() {
                    Ok(text) => match serde_json::from_str::<Vec<String>>(&text) {
                        Ok(names) => {
                            return names
                                .iter()
                                .map(|name| {
                                    Arc::new(
                                        ExternalExplorerBuilder::default()
                                            .name(name.to_owned())
                                            .url(self.url.to_owned())
                                            .client(self.client.to_owned())
                                            .build()
                                            .expect("Failed to build an external explorer. Should never fail."),
                                    )
                                })
                                .map(|x| x as Arc<dyn Explorer>)
                                .collect();
                        }
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
                }
            }
        }
        Vec::new()
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
