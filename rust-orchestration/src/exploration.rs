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
    headers::ExplorationBid, DecisionModel, ExplorationModule, ExplorationSolution,
};
use log::{debug, warn};
use serde::{Deserialize, Serialize};
use websocket::Message;

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
    fn from<T: DecisionModel + ?Sized>(
        explorer_name: &str,
        decision_model: &T,
    ) -> ExplorationRequestMessage {
        ExplorationRequestMessage {
            explorer_id: explorer_name.to_owned(),
            model_message: DecisionModelMessage::from(decision_model),
            previous_solutions: vec![],
        }
    }

    fn to_json_str(&self) -> String {
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
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Vec<ExplorationSolution> {
        if let Err(e) = self
            .http_post("set", &vec![("parameter", "max-sols")], &max_sols)
            .and(self.http_post("set", &vec![("parameter", "total-timeout")], &total_timeout))
            .and(self.http_post(
                "set",
                &vec![("parameter", "time-resolution")],
                &time_resolution,
            ))
            .and(self.http_post(
                "set",
                &vec![("parameter", "memory-resolution")],
                &memory_resolution,
            ))
        {
            warn!(
                "Could not set exploration parameters for module {}. Trying to continue.",
                self.unique_identifier()
            );
            debug!("Error is {}", e.to_string());
        };
        if let Ok(mut client) = websocket::ClientBuilder::new(
            format!("ws://{}:{}/explore", self.address, self.port).as_str(),
        ) {
            if let Ok(mut conn) = client.connect_insecure() {
                let message = ExplorationRequestMessage::from(explorer_id, m.as_ref());
                if let Ok(_) = conn.send_message(&Message::text(message.to_json_str())) {
                    return conn
                        .incoming_messages()
                        .take_while(|x| x.is_ok())
                        .flat_map(|x| x.ok())
                        .flat_map(|res| match res {
                            websocket::OwnedMessage::Text(sol_txt) => {
                                if let Some(sol) =
                                    ExplorationSolutionMessage::from_json_str(sol_txt.as_str())
                                {
                                    Some((
                                        Arc::new(OpaqueDecisionModel::from(&sol))
                                            as Arc<dyn DecisionModel>,
                                        sol.objectives,
                                    ))
                                } else {
                                    warn!(
                                        "Failed to deserialize exploration solution of module {}",
                                        self.unique_identifier()
                                    );
                                    None
                                }
                            }
                            _ => None,
                        })
                        .collect();
                };
            }
        }
        vec![]
    }

    fn iter_explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        _currrent_solutions: Vec<idesyde_core::ExplorationSolution>,
        exploration_configuration: idesyde_core::ExplorationConfiguration,
        solution_iter: fn(&ExplorationSolution) -> (),
    ) -> Vec<idesyde_core::ExplorationSolution> {
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
        if let Ok(mut client) = websocket::ClientBuilder::new(
            format!("ws://{}:{}/explore", self.address, self.port).as_str(),
        ) {
            if let Ok(mut conn) = client.connect_insecure() {
                let message = ExplorationRequestMessage::from(explorer_id, m.as_ref());
                if let Ok(_) = conn.send_message(&Message::text(message.to_json_str())) {
                    return conn
                        .incoming_messages()
                        .take_while(|x| x.is_ok())
                        .flat_map(|x| x.ok())
                        .flat_map(|res| match res {
                            websocket::OwnedMessage::Text(sol_txt) => {
                                if let Some(sol_message) =
                                    ExplorationSolutionMessage::from_json_str(sol_txt.as_str())
                                {
                                    let sol = (
                                        Arc::new(OpaqueDecisionModel::from(&sol_message))
                                            as Arc<dyn DecisionModel>,
                                        sol_message.objectives,
                                    );
                                    solution_iter(&sol);
                                    Some(sol)
                                } else {
                                    warn!(
                                        "Failed to deserialize exploration solution of module {}",
                                        self.unique_identifier()
                                    );
                                    None
                                }
                            }
                            _ => None,
                        })
                        .collect();
                };
            }
        }
        vec![]
    }
}

// #[derive(Debug, PartialEq, Eq, Hash)]
// pub struct ExternalExplorationModule {
//     pub command_path_: PathBuf,
//     pub identified_path_: PathBuf,
//     pub solved_path_: PathBuf,
// }

// impl ExternalExplorationModule {
//     pub fn is_java(&self) -> bool {
//         self.command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false)
//     }
// }

// impl ExplorationModule for ExternalExplorationModule {
//     fn unique_identifier(&self) -> String {
//         self.command_path_.to_str().unwrap().to_string()
//     }

//     // fn available_criterias(
//     //     &self,
//     //     _m: Box<dyn idesyde_core::DecisionModel>,
//     // ) -> std::collections::HashMap<String, f32> {
//     //     HashMap::new() // TODO: put interfaces later
//     // }

//     fn bid(&self, m: Arc<dyn idesyde_core::DecisionModel>) -> Vec<ExplorationBid> {
//         let headers = load_decision_model_headers_from_binary(&self.identified_path_);
//         let chosen_path = headers
//             .iter()
//             .find(|(_, h)| h == &m.header())
//             .map(|(p, _)| p)
//             .unwrap();
//         let output = match self.is_java() {
//             true => std::process::Command::new("java")
//                 .arg("-jar")
//                 .arg(&self.command_path_)
//                 .arg("-c")
//                 .arg(chosen_path)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//             false => std::process::Command::new(&self.command_path_)
//                 .arg("-c")
//                 .arg(chosen_path)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//         };
//         let o = output
//             .expect("Failed to get combination from exploration module.")
//             .stdout;
//         o.lines().flat_map(|l_or_err| l_or_err.ok()).map(|l| {
//             match serde_json::from_str(l.as_str()) {
//                 Ok(bid) => bid,
//                 Err(e) => {
//                     warn!("Failed to deserialize combination from exploration module. Assuming it cannot explore.");
//                     debug!("Given error is: {}", e.to_string());
//                     debug!(
//                         "Return output from the exploration module is: {}",
//                         std::str::from_utf8(&o).unwrap_or("NOT UTF8")
//                     );
//                     ExplorationBid {
//                         explorer_unique_identifier: self.unique_identifier(),
//                         decision_model_category: m.category(),
//                         can_explore: false,
//                         properties: HashMap::new(),
//                     }
//                 }
//             }
//         }).collect()
//     }

//     fn explore(
//         &self,
//         m: Arc<dyn idesyde_core::DecisionModel>,
//         explorer_id: &str,
//         max_sols: i64,
//         total_timeout: i64,
//         time_resolution: i64,
//         memory_resolution: i64,
//     ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>> {
//         let headers = load_decision_model_headers_from_binary(&self.identified_path_);
//         let chosen_path = headers
//             .iter()
//             .find(|(_, h)| h == &m.header())
//             .map(|(p, _)| p)
//             .unwrap();
//         let child_opt = match self.is_java() {
//             true => std::process::Command::new("java")
//                 .arg("-jar")
//                 .arg(&self.command_path_)
//                 .arg("-e")
//                 .arg(chosen_path)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .arg("-o")
//                 .arg(&self.solved_path_)
//                 .arg("-n")
//                 .arg(explorer_id.to_string())
//                 .arg("--maximum-solutions")
//                 .arg(format!("{}", max_sols))
//                 .arg("--total-timeout")
//                 .arg(format!("{}", total_timeout))
//                 .arg("--time-resolution")
//                 .arg(format!("{}", time_resolution))
//                 .arg("--memory-resolution")
//                 .arg(format!("{}", memory_resolution))
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .spawn(),
//             false => std::process::Command::new(&self.command_path_)
//                 .arg("-e")
//                 .arg(chosen_path)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .arg("-o")
//                 .arg(&self.solved_path_)
//                 .arg("-n")
//                 .arg(explorer_id.to_string())
//                 .arg("--maximum-solutions")
//                 .arg(format!("{}", max_sols))
//                 .arg("--total-timeout")
//                 .arg(format!("{}", total_timeout))
//                 .arg("--time-resolution")
//                 .arg(format!("{}", time_resolution))
//                 .arg("--memory-resolution")
//                 .arg(format!("{}", memory_resolution))
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .spawn(),
//         };
//         let uid = self.unique_identifier().clone();
//         let child = child_opt.expect("Failed to initiate explorer");
//         let out = child.stdout.expect("Failed to acquire explorer STDOUT");
//         let err = child.stderr.expect("Failed to achique explorer STDERR");
//         if BufReader::new(err).lines().flatten().any(|l| !l.is_empty()) {
//             warn!(
//                 "Exploration module {} produced error messages. Please check it for correctness.",
//                 uid
//             );
//         }
//         let buf = BufReader::new(out);
//         Box::new(
//             buf.lines()
//                 .map(|l| l.expect("Failed to read solution during exploration"))
//                 .flat_map(move |f| {
//                     let h = load_decision_model_header_from_path(Path::new(f.as_str()));
//                     if h.is_none() {
//                         warn!("Exploration module {} produced non-compliant output '{}' during exploration. Please check it for correctness.", uid, f);
//                     }
//                     h
//                 })
//                 .map(|m| Arc::new(m) as Arc<dyn DecisionModel>),
//         )
//     }

//     fn iter_explore(
//         &self,
//         _m: Arc<dyn DecisionModel>,
//         _explorer_id: &str,
//         _currrent_solutions: Vec<ExplorationSolution>,
//         _exploration_configuration: idesyde_core::ExplorationConfiguration,
//         _solution_iter: fn(ExplorationSolution) -> (),
//     ) -> Vec<ExplorationSolution> {
//         vec![]
//     }
// }
