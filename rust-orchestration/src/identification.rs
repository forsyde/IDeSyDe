use std::{
    collections::{HashSet, LinkedList},
    hash::Hash,
    io::BufRead,
    io::BufReader,
    net::{Ipv4Addr, TcpStream},
    path::PathBuf,
    process::{Child, Stdio},
    sync::{Arc, Mutex},
};

use derive_builder::Builder;
use rayon::prelude::*;

use idesyde_blueprints::IdentificationResultMessage;
use idesyde_core::{
    headers::{DecisionModelHeader, DesignModelHeader},
    DecisionModel, DesignModel, IdentificationIterator, IdentificationResult, Module,
    OpaqueDecisionModel, OpaqueDesignModel,
};
use log::{debug, warn};
use tungstenite::WebSocket;
use url::Url;

use crate::HttpServerLike;

#[derive(Debug, Clone)]
pub struct ExternalServerIdentificationModule {
    name: String,
    url: Url,
    client: Arc<reqwest::blocking::Client>,
    process: Option<Arc<Mutex<Child>>>,
}

impl ExternalServerIdentificationModule {
    pub fn try_create_local(command_path_: PathBuf) -> Option<ExternalServerIdentificationModule> {
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
                    return Some(ExternalServerIdentificationModule {
                        name: command_path_
                            .clone()
                            .file_name()
                            .and_then(|x| x.to_str())
                            .and_then(|x| x.split('.').next())
                            .expect("Could not fetch name from imodule file name.")
                            .to_string(),
                        url: Url::parse(&format!("http://127.0.0.1:{}", port))
                            .expect("Failed to build imodule url. Should always succeed."),
                        client: Arc::new(reqwest::blocking::Client::new()),
                        process: Some(Arc::new(Mutex::new(server_child))),
                    });
                }
            }
        }
        None
    }

    pub fn from(name: &str, url: &Url) -> ExternalServerIdentificationModule {
        ExternalServerIdentificationModule {
            name: name.to_string(),
            url: url.to_owned(),
            client: Arc::new(reqwest::blocking::Client::new()),
            process: None,
        }
    }
}

// impl LocalServerLike for ExternalServerIdentificationModule {
//     fn get_process(&self) -> Arc<Mutex<Child>> {
//         self.process.clone()
//     }
// }

impl PartialEq for ExternalServerIdentificationModule {
    fn eq(&self, other: &Self) -> bool {
        self.name == other.name && self.url == other.url
        // && self.inputs_path == other.inputs_path
        // && self.identified_path == other.identified_path
        // && self.solved_path == other.solved_path
        // && self.reverse_path == other.reverse_path
        // && self.output_path_ == other.output_path_
    }
}

impl Eq for ExternalServerIdentificationModule {}

impl Hash for ExternalServerIdentificationModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.name.hash(state);
    }
}

impl Drop for ExternalServerIdentificationModule {
    fn drop(&mut self) {
        if let Some(local_process) = self.process.clone() {
            if let Ok(mut server_guard) = local_process.lock() {
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

// impl HttpServerLike for ExternalServerIdentificationModule {
//     fn get_client(&self) -> Arc<reqwest::blocking::Client> {
//         self.client.clone()
//     }

//     fn get_address(&self) -> std::net::IpAddr {
//         self.address.to_owned()
//     }

//     fn get_port(&self) -> usize {
//         self.port
//     }
// }

#[derive(Builder, Clone)]
struct ExternalServerIdentifiticationIterator {
    design_models: HashSet<Arc<dyn DesignModel>>,
    decision_models: HashSet<Arc<dyn DecisionModel>>,
    decision_models_to_upload: HashSet<Arc<dyn DecisionModel>>,
    design_models_to_upload: HashSet<Arc<dyn DesignModel>>,
    websocket: Arc<WebSocket<TcpStream>>,
    messages: Vec<String>,
    waiting: bool,
}

impl Iterator for ExternalServerIdentifiticationIterator {
    type Item = Arc<dyn DecisionModel>;

    fn next(&mut self) -> Option<Self::Item> {
        // send the decision models
        for m in &self.decision_models_to_upload {
            if let Ok(decision_cbor) = OpaqueDecisionModel::from(m).to_cbor() {
                self.websocket
                    .send(tungstenite::Message::Binary(decision_cbor));
            }
        }
        self.decision_models
            .extend(self.decision_models_to_upload.drain());
        // same for design models
        for m in &self.design_models_to_upload {
            if let Ok(design_cbor) = OpaqueDesignModel::from(m.as_ref()).to_cbor() {
                self.websocket
                    .send(tungstenite::Message::Binary(design_cbor));
            };
        }
        self.design_models
            .extend(self.design_models_to_upload.drain());
        self.websocket
            .send(tungstenite::Message::Text("done".to_string()));
        while let Ok(message) = self.websocket.read() {
            match message {
                tungstenite::Message::Text(decision_json) => {
                    if let Ok(opaque) = OpaqueDecisionModel::from_json_str(decision_json.as_str()) {
                        let opaquea = Arc::new(opaque);
                        self.decision_models.insert(opaquea);
                        return Some(opaquea);
                    }
                }
                tungstenite::Message::Binary(decision_cbor) => {
                    if let Ok(opaque) = OpaqueDecisionModel::from_cbor(decision_cbor.as_slice()) {
                        let opaquea = Arc::new(opaque);
                        self.decision_models.insert(opaquea);
                        return Some(opaquea);
                    }
                }
                tungstenite::Message::Ping(_) => {
                    self.websocket.send(tungstenite::Message::Pong(vec![]));
                }
                tungstenite::Message::Pong(_) => {
                    self.websocket.send(tungstenite::Message::Ping(vec![]));
                }
                _ => (),
            }
        }
        None
        // first, try to see if no element is in the queue to be returned
        // if self.waiting {
        //     if let Ok(identified_url) = self.imodule.url.join("/identified") {
        //         while let Some(opaque) = self
        //             .imodule
        //             .client
        //             .get(identified_url)
        //             .query(&[("session", "0", "encoding", "cbor")])
        //             .send()
        //             .ok()
        //             .filter(|r| !r.status().as_str().eq_ignore_ascii_case("204"))
        //             .and_then(|r| r.bytes().ok())
        //             .map(|b| b.to_vec())
        //             .and_then(|v| OpaqueDecisionModel::from_cbor(v.as_slice()).ok())
        //         {
        //             let opaquea: Arc<dyn DecisionModel> = Arc::new(opaque);
        //             if !self.decision_models.contains(&opaquea) {
        //                 self.identified.push_back(opaquea);
        //                 self.decision_models.insert(opaquea);
        //             }
        //         }
        //         while let Some(opaque) = self
        //             .imodule
        //             .client
        //             .get(identified_url)
        //             .query(&[("session", "0", "encoding", "json")])
        //             .send()
        //             .ok()
        //             .filter(|r| !r.status().as_str().eq_ignore_ascii_case("204"))
        //             .and_then(|r| r.text().ok())
        //             .and_then(|b| OpaqueDecisionModel::from_json_str(b.as_str()).ok())
        //         {
        //             let opaquea: Arc<dyn DecisionModel> = Arc::new(opaque);
        //             if !self.decision_models.contains(&opaquea) {
        //                 self.identified.push_back(opaquea);
        //                 self.decision_models.insert(opaquea);
        //             }
        //         }
        //         self.waiting = false;
        //     }
        // }
        // if !self.identified.is_empty() {
        //     return self.identified.pop_front();
        // } else {
        //     // otehrwise, we proceed to ask for more decision models
        //     // now proceed to identification
        //     // send decision models
        //     for m in &self.decision_models_to_upload {
        //         if let Ok(decision_cbor) = OpaqueDecisionModel::from(m).to_cbor() {
        //             self.websocket
        //                 .send(tungstenite::Message::Binary(decision_cbor));
        //         }
        //     }
        //     //     if let Ok(mut decision_url) = self.imodule.url.join("/decision") {
        //     //         decision_url.set_query(Some("session=0"));
        //     //         let opaque = OpaqueDecisionModel::from(m);
        //     //         let mut form = reqwest::blocking::multipart::Form::new();
        //     //         if let Ok(cbor_body) = opaque.to_cbor::<Vec<u8>>() {
        //     //             form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
        //     //         } else if let Ok(json_body) = opaque.to_json() {
        //     //             form.text("json", json_body);
        //     //         }
        //     //         self.imodule.client.put(decision_url).multipart(form).send();
        //     //     };
        //     // });
        //     self.decision_models
        //         .extend(self.decision_models_to_upload.drain());
        //     // same for design models
        //     for m in &self.design_models_to_upload {
        //         if let Ok(design_cbor) = OpaqueDesignModel::from(m.as_ref()).to_cbor() {
        //             self.websocket
        //                 .send(tungstenite::Message::Binary(design_cbor));
        //         };
        //     }
        //     // self.design_models_to_upload.par_iter().for_each(|m| {
        //     //     if let Ok(mut design_url) = self.imodule.url.join("/design") {
        //     //         design_url.set_query(Some("session=0"));
        //     //         let mut form = reqwest::blocking::multipart::Form::new();
        //     //         let opaque = OpaqueDesignModel::from(m.as_ref());
        //     //         if let Ok(cbor_body) = opaque.to_cbor() {
        //     //             form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
        //     //         } else if let Ok(json_body) = opaque.to_json() {
        //     //             form.text("json", json_body);
        //     //         }
        //     //         self.imodule.client.put(design_url).multipart(form).send();
        //     //     };
        //     // });
        //     self.design_models
        //         .extend(self.design_models_to_upload.drain());
        //     if let Ok(identify_url) = self.imodule.url.join("/identify") {
        //         if let Ok(msgs) = self
        //             .imodule
        //             .client
        //             .post(identify_url)
        //             .query(&[("session", "0")])
        //             .send()
        //             .and_then(|r| r.text())
        //         {
        //             if let Ok(msgs_vec) = serde_json::from_str::<Vec<String>>(msgs.as_str()) {
        //                 self.messages.extend(msgs_vec.into_iter());
        //             }
        //         };
        //     }
        //     self.waiting = true;
        //     return self.next();
        // }
    }
}

impl IdentificationIterator for ExternalServerIdentifiticationIterator {
    fn next_with_models(
        &mut self,
        decision_models: &HashSet<Arc<dyn DecisionModel>>,
        design_models: &HashSet<Arc<dyn DesignModel>>,
    ) -> Option<Arc<dyn DecisionModel>> {
        self.decision_models_to_upload
            .extend(decision_models.iter().map(|x| x.to_owned()));
        self.design_models_to_upload
            .extend(design_models.iter().map(|x| x.to_owned()));
        return self.next();
    }

    fn collect_messages(&mut self) -> Vec<(String, String)> {
        self.messages
            .iter()
            .map(|msg| ("DEBUG".to_owned(), msg.to_owned()))
            .collect()
    }
}

impl Module for ExternalServerIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.name.clone()
    }

    fn start_identification(
        &self,
        initial_design_models: &HashSet<Arc<dyn DesignModel>>,
        initial_decision_models: &HashSet<Arc<dyn DecisionModel>>,
    ) -> Box<dyn idesyde_core::IdentificationIterator> {
        let mut mut_url = self.url.clone();
        mut_url.set_scheme("ws");
        if let Ok(identify_url) = mut_url.join("/identify") {
            if let Some((ws, _)) = std::net::TcpStream::connect(mut_url.as_str())
                .ok()
                .and_then(|stream| tungstenite::client(identify_url, stream).ok())
            {
                return Box::new(
                    ExternalServerIdentifiticationIteratorBuilder::default()
                        .decision_models(initial_decision_models.to_owned())
                        .design_models(initial_design_models.to_owned())
                        .websocket(Arc::new(ws))
                        .build()
                        .expect("Failed building external server iterator. Should never fail."),
                );
            }
        }
        Box::new(idesyde_core::empty_identification_iter())
        // self.write_line_to_input(
        //     format!("SET identified-path {}", self.identified_path.display()).as_str(),
        // );
        // self.write_line_to_input(
        //     format!("SET solved-path {}", self.solved_path.display()).as_str(),
        // );
        // self.write_line_to_input(
        //     format!("SET design-path {}", self.inputs_path.display()).as_str(),
        // );
        // self.write_line_to_input(
        //     format!("SET reverse-path {}", self.reverse_path.display()).as_str(),
        // );
        // let mut form = reqwest::blocking::multipart::Form::new();
        // for (i, design_model) in design_models.iter().enumerate() {
        //     // let message = DesignModelMessage::from_dyn_design_model(design_model.as_ref());
        //     form = form.part(
        //         format!("designModel{}", i),
        //         reqwest::blocking::multipart::Part::text(
        //             DesignModelMessage::from(design_model).to_json_str(),
        //         ),
        //     );
        //     // match self.send_design(design_model.as_ref()) {
        //     //     Ok(_) => {}
        //     //     Err(e) => warn!(
        //     //         "Module {} had an error while recieving a design model at {}.",
        //     //         self.unique_identifier(),
        //     //         e.url().map(|x| x.as_str()).unwrap_or("<MissingUrl>")
        //     //     ),
        //     // }
        //     // self.write_line_to_input(format!("DESIGN {}", message.to_json_str()).as_str())
        //     //     .expect("Error at writing");
        //     // println!("DESIGN {}", message.to_json_str());
        // }
        // for (i, decision_model) in decision_models.iter().enumerate() {
        //     // let message = DecisionModelMessage::from_dyn_decision_model(decision_model.as_ref());
        //     // if let Err(e) = self.send_decision(decision_model.as_ref()) {
        //     //     warn!(
        //     //         "Module {} had an error while recieving a decision model at {}.",
        //     //         self.unique_identifier(),
        //     //         e.url().map(|x| x.as_str()).unwrap_or("<MissingUrl>")
        //     //     );
        //     // }
        //     form = form.part(
        //         format!("decisionModel{}", i),
        //         reqwest::blocking::multipart::Part::text(
        //             DecisionModelMessage::from(decision_model).to_json_str(),
        //         ),
        //     );
        //     // let h = decision_model.header();
        //     // self.write_line_to_input(format!("DECISION INLINE {}", message.to_json_str()).as_str())
        //     //     .expect("Error at writing");
        // }
        // match self
        //     .get_client()
        //     .post(format!(
        //         "http://{}:{}/identify",
        //         self.get_address(),
        //         self.get_port()
        //     ))
        //     .multipart(form)
        //     .send()
        //     .and_then(|x| x.text())
        // {
        //     Ok(response) => match IdentificationResultMessage::try_from(response.as_str()) {
        //         Ok(v) => {
        //             return (
        //                 v.identified
        //                     .iter()
        //                     .map(|x| {
        //                         Arc::new(OpaqueDecisionModel::from(x)) as Arc<dyn DecisionModel>
        //                     })
        //                     .collect(),
        //                 v.errors,
        //             );
        //         }
        //         Err(e) => {
        //             warn!(
        //                 "Module {} produced an error at identification. Check it for correctness",
        //                 self.unique_identifier()
        //             );
        //             debug!(
        //                 "Module {} error: {}",
        //                 self.unique_identifier(),
        //                 e.to_string()
        //             );
        //             debug!("Response was: {}", response.as_str());
        //         }
        //     },
        //     Err(err) => {
        //         warn!(
        //             "Had an error while recovering identification results from module {}. Attempting to continue.",
        //             self.unique_identifier()
        //         );
        //         debug!("Recv error is: {}", err.to_string());
        //     }
        // }
        // self.write_line_to_input(format!("IDENTIFY {}", iteration).as_str());
        // if let Ok(response) = self
        //     .send_command(
        //         "identify",
        //         &vec![("iteration", format!("{}", iteration).as_str())],
        //     )
        //     .and_then(|x| x.text())
        // {
        //     match IdentificationResultMessage::try_from(response.as_str()) {
        //         Ok(v) => {
        //             return (
        //                 v.identified
        //                     .iter()
        //                     .map(|x| {
        //                         Arc::new(OpaqueDecisionModel::from(x)) as Arc<dyn DecisionModel>
        //                     })
        //                     .collect(),
        //                 v.errors,
        //             );
        //         }
        //         Err(e) => {
        //             warn!(
        //                 "Module {} produced an error at identification. Check it for correctness",
        //                 self.unique_identifier()
        //             );
        //             debug!(
        //                 "Module {} error: {}",
        //                 self.unique_identifier(),
        //                 e.to_string()
        //             );
        //             debug!("Response was: {}", response.as_str());
        //         }
        //     }
        // };
        // (vec![], HashSet::new())
        // self.map_output(|buf| {
        //     buf.lines()
        //         .flatten()
        //         // .inspect(|l| {
        //         //     println!("{}", l);
        //         //     println!("{},", self.read_all_err().unwrap_or("nothing".to_string()));
        //         // })
        //         .take_while(|line| !line.trim().eq_ignore_ascii_case("FINISHED"))
        //         .filter(|line| {
        //             if line.trim().starts_with("DECISION") {
        //                 true
        //             } else {
        //                 warn!(
        //                     "Ignoring non-compliant identification result by module {}: {}",
        //                     self.unique_identifier(),
        //                     line
        //                 );
        //                 debug!(
        //                     "module {} error: {}",
        //                     self.unique_identifier(),
        //                     self.read_line_from_err()
        //                         .unwrap_or("Unable to capture".to_string())
        //                 );
        //                 false
        //             }
        //         })
        //         .map(|line| {
        //             let payload = &line[15..].trim();
        //             let mes = DecisionModelMessage::from_json_str(payload);
        //             if let Some(message) = mes {
        //                 let boxed = Arc::new(OpaqueDecisionModel::from_decision_message(&message))
        //                     as Arc<dyn DecisionModel>;
        //                 return Some(boxed);
        //             }
        //             None
        //         })
        //         .flatten()
        //         .collect()
        // })
        // .unwrap_or(Vec::new())
    }

    fn reverse_identification(
        &self,
        solved_decision_models: &HashSet<Arc<dyn DecisionModel>>,
        design_models: &HashSet<Arc<dyn DesignModel>>,
    ) -> Box<dyn Iterator<Item = Arc<dyn DesignModel>>> {
        // let mut integrated: Vec<Box<dyn DesignModel>> = Vec::new();
        // send decision models
        solved_decision_models.par_iter().for_each(|m| {
            if let Ok(mut decision_url) = self.url.join("/explored") {
                decision_url.set_query(Some("session=0"));
                let opaque = OpaqueDecisionModel::from(m);
                let mut form = reqwest::blocking::multipart::Form::new();
                if let Ok(cbor_body) = opaque.to_cbor::<Vec<u8>>() {
                    form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
                } else if let Ok(json_body) = opaque.to_json() {
                    form.text("json", json_body);
                }
                self.client.put(decision_url).multipart(form).send();
            };
        });
        // same for design models
        design_models.par_iter().for_each(|m| {
            if let Ok(mut design_url) = self.url.join("/design") {
                design_url.set_query(Some("session=0"));
                let mut form = reqwest::blocking::multipart::Form::new();
                let opaque = OpaqueDesignModel::from(m.as_ref());
                if let Ok(cbor_body) = opaque.to_cbor() {
                    form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
                } else if let Ok(json_body) = opaque.to_json() {
                    form.text("json", json_body);
                }
                self.client.put(design_url).multipart(form).send();
            };
        });
        // ask to reverse
        if let Ok(reverse_url) = self.url.join("/reverse") {
            self.client
                .post(reverse_url)
                .query(&[("session", "0")])
                .send();
        }
        // now collect the reversed models
        if let Ok(reversed_url) = self.url.join("/reversed") {
            let client = self.client.to_owned();
            return Box::new(
                std::iter::repeat_with(|| {
                    client
                        .get(reversed_url)
                        .query(&[("session", "0", "encoding", "cbor")])
                        .send()
                        .ok()
                        .filter(|r| !r.status().as_str().eq_ignore_ascii_case("204"))
                        .and_then(|r| r.bytes().ok())
                        .map(|b| b.to_vec())
                        .and_then(|v| OpaqueDesignModel::from_cbor(v.as_slice()).ok())
                        .or_else(|| {
                            client
                                .get(reversed_url)
                                .query(&[("session", "0", "encoding", "json")])
                                .send()
                                .ok()
                                .filter(|r| !r.status().as_str().eq_ignore_ascii_case("204"))
                                .and_then(|r| r.text().ok())
                                .and_then(|b| OpaqueDesignModel::from_json_str(b.as_str()).ok())
                        })
                        .map(|x| Arc::new(x) as Arc<dyn DesignModel>)
                })
                .flatten(),
            );
        } else {
            return Box::new(std::iter::empty());
        }
        // if let Ok(identified_url) = self.imodule.url.join("/identified") {
        //     while let Some(opaque) = self
        //         .imodule
        //         .client
        //         .get(identified_url)
        //         .query(&[("session", "0", "encoding", "cbor")])
        //         .send()
        //         .ok()
        //         .and_then(|r| r.bytes().ok())
        //         .map(|b| b.to_vec())
        //         .and_then(|v| OpaqueDecisionModel::from_cbor(v.as_slice()).ok())
        //     {
        //         let opaquea: Arc<dyn DecisionModel> = Arc::new(opaque);
        //         if !self.decision_models.contains(&opaquea) {
        //             self.identified.push_back(opaquea);
        //             self.decision_models.insert(opaquea);
        //         }
        //     }
        //     while let Some(opaque) = self
        //         .imodule
        //         .client
        //         .get(identified_url)
        //         .query(&[("session", "0", "encoding", "json")])
        //         .send()
        //         .ok()
        //         .and_then(|r| r.text().ok())
        //         .and_then(|b| OpaqueDecisionModel::from_json_str(b.as_str()).ok())
        //     {
        //         let opaquea: Arc<dyn DecisionModel> = Arc::new(opaque);
        //         if !self.decision_models.contains(&opaquea) {
        //             self.identified.push_back(opaquea);
        //             self.decision_models.insert(opaquea);
        //         }
        //     }
        //     self.waiting = false;
        // }
        // let mut form = reqwest::blocking::multipart::Form::new();
        // for (i, design_model) in design_models.iter().enumerate() {
        //     form = form.part(
        //         format!("designModel{}", i),
        //         reqwest::blocking::multipart::Part::text(
        //             OpaqueDesignModel::from(design_model.as_ref())
        //                 .to_json()
        //                 .expect("Failed to make JSON out of design model. Should never fail."),
        //         ),
        //     );
        // }
        // for (i, decision_model) in solved_decision_models.iter().enumerate() {
        //     form = form.part(
        //         format!("decisionModel{}", i),
        //         reqwest::blocking::multipart::Part::text(
        //             OpaqueDecisionModel::from(decision_model)
        //                 .to_json()
        //                 .expect("Failed to make JSON out of a decision model. Should never fail."),
        //         ),
        //     );
        // }
        // if let Ok(reverse_url) = self.url.join("/reverse") {
        //     match self
        //         .client
        //         .post(reverse_url)
        //         .multipart(form)
        //         .send()
        //         .and_then(|x| x.text())
        //     {
        //         Ok(response) => {
        //             match serde_json::from_str::<Vec<OpaqueDesignModel>>(response.as_str()) {
        //                 Ok(v) => {
        //                     return v
        //                         .iter()
        //                         .map(|x| {
        //                             Arc::new(OpaqueDesignModel::from(x)) as Arc<dyn DesignModel>
        //                         })
        //                         .collect();
        //                 }
        //                 Err(e) => {
        //                     warn!(
        //                     "Module {} produced an error at identification. Check it for correctness",
        //                     self.unique_identifier()
        //                 );
        //                     debug!(
        //                         "Module {} error: {}",
        //                         self.unique_identifier(),
        //                         e.to_string()
        //                     );
        //                     debug!("Response was: {}", response.as_str());
        //                 }
        //             }
        //         }
        //         Err(err) => {
        //             warn!(
        //                 "Had an error while recovering identification results from module {}. Attempting to continue.",
        //                 self.unique_identifier()
        //             );
        //             debug!("Recv error is: {}", err.to_string());
        //         }
        //     }
        // }
        // vec![]
    }
}

pub fn identification_procedure(
    imodules: &HashSet<Arc<dyn Module>>,
    design_models: &HashSet<Arc<dyn DesignModel>>,
    pre_identified: &HashSet<Arc<dyn DecisionModel>>,
    starting_iter: i32,
) -> (HashSet<Arc<dyn DecisionModel>>, HashSet<(String, String)>) {
    let mut step = starting_iter;
    let mut fix_point = false;
    let mut identified: HashSet<Arc<dyn DecisionModel>> = HashSet::new();
    let mut messages: HashSet<(String, String)> = HashSet::new();
    let mut iterators: Vec<Box<dyn IdentificationIterator>> = imodules
        .iter()
        .map(|imodule| imodule.start_identification(design_models, &identified))
        .collect();
    identified.extend(pre_identified.iter().map(|x| x.to_owned()));
    while !fix_point {
        // try to eliminate opaque repeated decision models
        // while let Some((idx, _)) = identified
        //     .iter()
        //     .enumerate()
        //     .filter(|(_, m)| m.downcast_ref::<OpaqueDecisionModel>().is_some())
        //     .find(|(_, opaque)| {
        //         identified
        //             .iter()
        //             .any(|x| &x == opaque && x.downcast_ref::<OpaqueDecisionModel>().is_none())
        //     })
        // {
        //     identified.remove(idx);
        // }
        // the step condition forces the procedure to go at least one more, fundamental for incrementability
        fix_point = true;
        let before = identified.len();
        let identified_step: HashSet<Arc<dyn DecisionModel>> = iterators
            .par_iter()
            .map(|iter| iter.next_with_models(&identified, design_models))
            .flatten()
            .collect();
        // .reduce(
        //     || (vec![], HashSet::new()),
        //     |(mut m1, mut e1), (m2, e2)| {
        //         m1.extend(m2);
        //         e1.extend(e2);
        //         (m1, e1)
        //     },
        // );
        for m in identified_step {
            if !identified.contains(&m) {
                identified.insert(m);
            }
        }
        let ident_messages: HashSet<(String, String)> = iterators
            .par_iter()
            .flat_map(|iter| iter.collect_messages())
            .collect();
        for (lvl, msg) in ident_messages {
            if lvl.contains("DEBUG") || lvl.contains("debug") {
                debug!("{}", msg);
            }
            messages.insert((lvl, msg));
        }
        // .filter(|potential| !identified.contains(potential))
        // .collect();
        // this contain check is done again because there might be imodules that identify the same decision model,
        // and since the filtering before is step-based, it would add both identical decision models.
        // This new for-if fixes this by checking every model of this step.
        debug!(
            "{} total decision models identified at step {}",
            identified.len(),
            step
        );
        fix_point = fix_point && (identified.len() == before);
        step += 1;
    }
    (identified, messages)
}

// #[derive(Debug, PartialEq, Eq, Hash)]
// pub struct ExternalIdentificationModule {
//     pub command_path_: PathBuf,
//     pub inputs_path_: PathBuf,
//     pub identified_path_: PathBuf,
//     pub solved_path_: PathBuf,
//     pub reverse_path_: PathBuf,
//     pub output_path_: PathBuf,
// }

// impl ExternalIdentificationModule {
//     pub fn is_java(&self) -> bool {
//         self.command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false)
//     }
// }

// impl IdentificationModule for ExternalIdentificationModule {
//     fn unique_identifier(&self) -> String {
//         self.command_path_.to_str().unwrap().to_string()
//     }

//     fn identification_step(
//         &self,
//         iteration: i32,
//         _design_models: &Vec<Arc<dyn DesignModel>>,
//         _decision_models: &Vec<Arc<dyn DecisionModel>>,
//     ) -> IdentificationResult {
//         let is_java = self
//             .command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false);
//         let output = match is_java {
//             true => std::process::Command::new("java")
//                 .arg("-jar")
//                 .arg(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .arg("-t")
//                 .arg(iteration.to_string())
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//             false => std::process::Command::new(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .arg("-t")
//                 .arg(iteration.to_string())
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//         };
//         if let Ok(out) = output {
//             if let Ok(s) = String::from_utf8(out.stdout) {
//                 let identified: Vec<Arc<dyn DecisionModel>> = s
//                     .lines()
//                     .flat_map(|p| {
//                         if let Ok(b) = std::fs::read(p) {
//                             if let Ok(header) = rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()) {
//                                 return Some(Arc::new(header) as Arc<dyn DecisionModel>);
//                             } else {
//                                 warn!("Failed to deserialize header coming from {}. Check this module for correctness.", self.unique_identifier());
//                             }
//                         } else {
//                             warn!("Unexpected header file from module {}. Check this module for correctness.", self.unique_identifier())
//                         }
//                         None
//                     })
//                     .collect();
//                 return (identified, HashSet::new());
//             }
//         }
//         (Vec::new(), HashSet::new())
//     }

//     fn reverse_identification(
//         &self,
//         _decision_model: &Vec<Arc<dyn DecisionModel>>,
//         _design_model: &Vec<Arc<dyn DesignModel>>,
//     ) -> Vec<Arc<dyn DesignModel>> {
//         let is_java = self
//             .command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false);
//         let output = match is_java {
//             true => std::process::Command::new("java")
//                 .arg("-jar")
//                 .arg(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-s")
//                 .arg(&self.solved_path_)
//                 .arg("-r")
//                 .arg(&self.reverse_path_)
//                 .arg("-o")
//                 .arg(&self.output_path_)
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//             false => std::process::Command::new(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-s")
//                 .arg(&self.solved_path_)
//                 .arg("-r")
//                 .arg(&self.reverse_path_)
//                 .arg("-o")
//                 .arg(&self.output_path_)
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//         };
//         if let Ok(out) = output {
//             if let Ok(s) = String::from_utf8(out.stdout) {
//                 let reversed: Vec<Arc<dyn DesignModel>> = s
//                     .lines()
//                     .map(|p| {
//                         let b = std::fs::read(p)
//                             .expect("Failed to read header file from disk during identification");
//                         let header = rmp_serde::from_slice::<DesignModelHeader>(b.as_slice())
//                             .expect(
//                             "Failed to deserialize header file from disk during identification.",
//                         );
//                         Arc::new(header) as Arc<dyn DesignModel>
//                     })
//                     .collect();
//                 return reversed;
//             }
//         }
//         Vec::new()
//     }
// }
