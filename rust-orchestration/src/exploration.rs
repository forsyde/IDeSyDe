use std::{
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
    pub conn: tungstenite::WebSocket<tungstenite::stream::MaybeTlsStream<std::net::TcpStream>>,
}

impl HttpServerSolutionIter {
    /// This creation function assumes that the channel is already sending back solutions
    pub fn new<I>(c: I) -> HttpServerSolutionIter
    where
        I: Into<tungstenite::WebSocket<tungstenite::stream::MaybeTlsStream<std::net::TcpStream>>>,
    {
        return HttpServerSolutionIter { conn: c.into() };
    }
}

impl Iterator for HttpServerSolutionIter {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        if self.conn.can_read() {
            if let Ok(sol_txt) = self.conn.read().and_then(|x| x.into_text()) {
                if let Some(sol) = ExplorationSolutionMessage::from_json_str(sol_txt.as_str()) {
                    return Some((
                        Arc::new(OpaqueDecisionModel::from(&sol)) as Arc<dyn DecisionModel>,
                        sol.objectives,
                    ));
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
        if let Ok((mut conn, _)) =
            tungstenite::connect(format!("ws://{}:{}/explore", self.address, self.port).as_str())
        {
            let message = ExplorationRequestMessage::with_previous_solutions(
                explorer_id,
                m.as_ref(),
                currrent_solutions
                    .iter()
                    .map(|x| ExplorationSolutionMessage::from(x))
                    .collect(),
            );
            if let Ok(_) = conn.send(tungstenite::Message::text(message.to_json_str())) {
                return Box::new(HttpServerSolutionIter::new(conn));
            }
        }
        Box::new(std::iter::empty())
    }
}
