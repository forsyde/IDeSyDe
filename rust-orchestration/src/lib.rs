pub mod exploration;
pub mod identification;

use std::borrow::BorrowMut;
use std::cmp::Ordering;
use std::collections::HashSet;
use std::hash::Hash;

use std::io::BufRead;
use std::io::BufReader;
use std::io::BufWriter;
use std::io::Write;

use std::ops::DerefMut;
use std::path::Path;

use std::path::PathBuf;
use std::process::Child;
use std::process::ChildStdout;
use std::process::Stdio;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Duration;

use exploration::ExternalExplorerBuilder;

use identification::ExternalServerIdentifiticationIterator;
use idesyde_blueprints::IdentificationResultCompactMessage;
use idesyde_bridge_java::java_modules_from_jar_paths;
use idesyde_core::DecisionModel;
use idesyde_core::DesignModel;
use idesyde_core::Explorer;
use idesyde_core::IdentificationResult;
use idesyde_core::Module;

use idesyde_core::OpaqueDecisionModel;
use idesyde_core::OpaqueDesignModel;
use log::debug;
use log::warn;
use rayon::prelude::*;
use reqwest::blocking::multipart::Form;
use reqwest::blocking::multipart::Part;
use serde::de;
use tungstenite::protocol::WebSocketConfig;
use url::Url;

use base64::{engine::general_purpose, Engine as _};

trait LocalServerLike {
    fn get_process(&self) -> Arc<Mutex<Child>>;

    fn write_line_to_input(&self, s: &str) -> Option<()> {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(childin) = &mut server.stdin {
                let mut buf = BufWriter::new(childin);
                let res = writeln!(buf, "{}", s);
                return buf.flush().and(res).ok();
            }
        }
        None
    }

    fn read_line_from_output(&self) -> Option<String> {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(out) = &mut server.stdout {
                let mut buf = BufReader::new(out);
                let mut line: String = "".to_string();
                if let Ok(_) = buf.read_line(&mut line) {
                    return Some(line.trim().to_string());
                };
            }
        }
        None
    }

    fn read_lines_from_output(&self) -> Vec<String> {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(out) = &mut server.stdout {
                let buf = BufReader::new(out);
                return buf.lines().flatten().collect();
            }
        }
        vec![]
    }

    fn map_output<F, O>(&self, f: F) -> Option<O>
    where
        F: Fn(BufReader<&mut ChildStdout>) -> O,
    {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(out) = &mut server.stdout {
                return Some(f(BufReader::new(out)));
            }
        }
        None
    }

    fn read_line_from_err(&self) -> Option<String> {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(out) = &mut server.stderr {
                let mut buf = BufReader::new(out);
                let mut line = "".to_string();
                return match buf.read_line(line.borrow_mut()) {
                    Ok(_) => Some(line.to_owned()),
                    Err(_) => None,
                };
                // return buf.lines().map(|x| x.ok()).;
            }
        }
        None
    }
}

#[derive(Debug, Clone)]
pub struct ExternalServerModule {
    name: String,
    url: Url,
    client: Arc<reqwest::blocking::Client>,
    process: Option<Arc<Mutex<Child>>>,
}

impl ExternalServerModule {
    pub fn try_create_local(command_path_: PathBuf) -> Option<ExternalServerModule> {
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
                .stdin(Stdio::null())
                .stdout(Stdio::piped())
                .stderr(Stdio::null())
                .spawn(),
            false => std::process::Command::new(&command_path_)
                .arg("--server")
                .arg("http")
                .stdin(Stdio::null())
                .stdout(Stdio::piped())
                .stderr(Stdio::null())
                .spawn(),
        };
        // the test involves just exitting it
        if let Ok(mut server_child) = child_res {
            if let Some(childout) = &mut server_child.stdout {
                let buf = BufReader::new(childout);
                let initialized_line_opt = buf
                    .lines()
                    .flatten()
                    .filter(|l| l.starts_with("INITIALIZED"))
                    .next();
                if let Some(initialized_line) = initialized_line_opt {
                    let mut split = initialized_line[12..].split(" ");
                    let port_opt = split.next().and_then(|x| x.parse::<usize>().ok());
                    if let Some(port) = port_opt {
                        let name = split
                            .next()
                            .unwrap_or(
                                command_path_
                                    .clone()
                                    .file_name()
                                    .and_then(|x| x.to_str())
                                    .and_then(|x| x.split('.').next())
                                    .expect("Could not fetch name from imodule file name."),
                            )
                            .to_string();
                        return Some(ExternalServerModule {
                            name,
                            url: Url::parse(&format!("http://127.0.0.1:{}", port))
                                .expect("Failed to build imodule url. Should always succeed."),
                            client: Arc::new(reqwest::blocking::Client::new()),
                            process: Some(Arc::new(Mutex::new(server_child))),
                        });
                    }
                }
            }
        }
        None
    }

    pub fn from(url: &Url, default_name: &str) -> ExternalServerModule {
        let name = url
            .join("/info/unique_identifier")
            .ok()
            .and_then(|u| reqwest::blocking::get(u).ok())
            .and_then(|res| res.text().ok())
            .unwrap_or(default_name.to_string());
        ExternalServerModule {
            name,
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

impl PartialEq for ExternalServerModule {
    fn eq(&self, other: &Self) -> bool {
        self.name == other.name && self.url == other.url
        // && self.inputs_path == other.inputs_path
        // && self.identified_path == other.identified_path
        // && self.solved_path == other.solved_path
        // && self.reverse_path == other.reverse_path
        // && self.output_path_ == other.output_path_
    }
}

impl Eq for ExternalServerModule {}

impl Hash for ExternalServerModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.name.hash(state);
    }
}

impl Drop for ExternalServerModule {
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

impl Module for ExternalServerModule {
    fn unique_identifier(&self) -> String {
        self.name.clone()
    }

    fn identification_step(
        &self,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> IdentificationResult {
        // let mut mut_url = self.url.clone();
        // mut_url
        //     .set_scheme("ws")
        //     .expect("Failed to set scheme to 'ws'.");
        // if let Ok(identify_url) = mut_url.join("/identify") {
        //     if let Some((ws, _)) = mut_url
        //         .socket_addrs(|| None)
        //         .ok()
        //         .and_then(|addrs| addrs.first().cloned())
        //         .and_then(|addr| std::net::TcpStream::connect(addr).ok())
        //         .and_then(|stream| tungstenite::client(identify_url, stream).ok())
        //     {
        //         return Box::new(ExternalServerIdentifiticationIterator::new(
        //             initial_design_models,
        //             initial_decision_models,
        //             ws,
        //         ));
        //     }
        // }
        // Box::new(idesyde_core::empty_identification_iter())
        design_models
            .par_iter()
            .filter(|m| {
                // let mut form = Form::new();
                // form = form.text("category", m.category());
                // for e in m.elements() {
                //     form = form.text("elements", e);
                // }
                let hash = m.global_sha2_hash();
                if let Ok(cache_url) = self.url.join("/design/cache/exists") {
                    return self
                        .client
                        .get(cache_url)
                        .body(hash)
                        .send()
                        .ok()
                        .and_then(|r| r.text().ok())
                        .map(|x| x.eq_ignore_ascii_case("false"))
                        .unwrap_or(true);
                }
                true
            })
            .map(|m| OpaqueDesignModel::from(m.as_ref()))
            .for_each(|m| {
                if let Ok(bodyj) = m.to_json() {
                    if let Ok(design_add_url) = self.url.join("/design/cache/add") {
                        let form = Form::new().text("designModel", bodyj);
                        if let Err(e) = self.client.post(design_add_url).multipart(form).send() {
                            debug!(
                                "Failed to send design model to identify with: {}",
                                e.to_string()
                            );
                        };
                    }
                }
            });
        decision_models
            .par_iter()
            .filter(|m| {
                // let mut form = Form::new();
                // form = form.text("category", m.category());
                // for e in m.part() {
                //     form = form.text("part", e);
                // }
                let hash = m.global_sha2_hash();
                if let Ok(cache_url) = self.url.join("/decision/cache/exists") {
                    return self
                        .client
                        .get(cache_url)
                        .body(hash)
                        .send()
                        .ok()
                        .and_then(|r| r.text().ok())
                        .map(|x| x.eq_ignore_ascii_case("false"))
                        .unwrap_or(true);
                }
                true
            })
            .map(|m| OpaqueDecisionModel::from(m.as_ref()))
            .for_each(|m| {
                // debug!(
                //     "Sending {:?} to module {:?}",
                //     m.category(),
                //     self.unique_identifier()
                // );
                if let Ok(bodyj) = m.to_json() {
                    if let Ok(decision_add_url) = self.url.join("/decision/cache/add") {
                        let form = Form::new().text("decisionModel", bodyj);
                        if let Err(e) = self.client.post(decision_add_url).multipart(form).send() {
                            debug!(
                                "Failed to send design model to identify  with: {}",
                                e.to_string()
                            );
                        };
                    }
                }
            });
        if let Ok(identify) = self.url.join("/identify") {
            if let Some(identified_message) = self
                .client
                .post(identify)
                // .multipart(form)
                .send()
                .ok()
                .and_then(|res| res.text().ok())
                .and_then(|txt| {
                    // debug!("Received identification result: {}", txt.as_str());
                    serde_json::from_str::<IdentificationResultCompactMessage>(txt.as_str()).ok()
                })
            {
                let reversed_hashes = identified_message
                    .identified
                    .iter()
                    .map(|s| general_purpose::STANDARD_NO_PAD.decode(s).ok())
                    .flatten()
                    .collect::<Vec<Vec<u8>>>();
                // debug!("Reversed hashes: {:?}", reversed_hashes);
                let identified_models = reversed_hashes
                    .into_par_iter()
                    .flat_map(|hash| {
                        // let hash_str = general_purpose::STANDARD.encode(hash);
                        if let Ok(cache_url) = self.url.join("/decision/cache/fetch") {
                            return self
                                .client
                                .get(cache_url)
                                .body(hash)
                                .send()
                                .ok()
                                .and_then(|r| r.text().ok())
                                .and_then(|txt| {
                                    OpaqueDecisionModel::from_json_str(txt.as_str()).ok()
                                })
                                .map(|x| Arc::new(x) as Arc<dyn DecisionModel>);
                        }
                        None
                    })
                    .collect();
                return (
                    identified_models,
                    identified_message.messages.into_iter().collect(),
                );
            }
        }
        (vec![], vec![])
    }

    fn reverse_identification(
        &self,
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> Vec<Arc<dyn DesignModel>> {
        // let mut mut_url = self.url.clone();
        // mut_url
        //     .set_scheme("ws")
        //     .expect("Failed to set scheme to 'ws'.");
        if let Ok(reverse_url) = self.url.join("/reverse") {
            design_models
                .par_iter()
                .filter(|m| {
                    let hash = m.global_sha2_hash();
                    if let Ok(cache_url) = self.url.join("/design/cache/exists") {
                        return self
                            .client
                            .get(cache_url)
                            .body(hash)
                            .send()
                            .ok()
                            .and_then(|r| r.text().ok())
                            .map(|x| x.eq_ignore_ascii_case("false"))
                            .unwrap_or(true);
                    }
                    true
                })
                .map(|m| OpaqueDesignModel::from(m.as_ref()))
                .for_each(|m| {
                    if let Ok(bodyj) = m.to_json() {
                        if let Ok(design_add_url) = self.url.join("/design/cache/add") {
                            let form = Form::new().text("designModel", bodyj);
                            if let Err(e) = self.client.post(design_add_url).multipart(form).send()
                            {
                                debug!(
                                    "Failed to send design model to reverse with: {}",
                                    e.to_string()
                                );
                            };
                        }
                    }
                });
            solved_decision_models
                .par_iter()
                .filter(|m| {
                    let hash = m.global_sha2_hash();
                    if let Ok(cache_url) = self.url.join("/solved/cache/exists") {
                        return self
                            .client
                            .get(cache_url)
                            .body(hash)
                            .send()
                            .ok()
                            .and_then(|r| r.text().ok())
                            .map(|x| x.eq_ignore_ascii_case("false"))
                            .unwrap_or(true);
                    }
                    true
                })
                .map(|m| OpaqueDecisionModel::from(m.as_ref()))
                .for_each(|m| {
                    if let Ok(bodyj) = m.to_json() {
                        if let Ok(decision_add_url) = self.url.join("/solved/cache/add") {
                            let form = Form::new().text("solvedModel", bodyj);
                            if let Err(e) =
                                self.client.post(decision_add_url).multipart(form).send()
                            {
                                debug!(
                                    "Failed to send design model to reverse with: {}",
                                    e.to_string()
                                );
                            };
                        }
                    }
                });
            // let mut form = Form::new();
            // for m in opaques {
            //     if let Ok(bodyj) = m.to_json() {
            //         form = form.text(format!("design{}", m.category()), bodyj);
            //     }
            // }
            // for m in solved {
            //     if let Ok(bodyj) = m.to_json() {
            //         // let part = Part::text(bodyj);
            //         form = form.text(format!("solved{}", m.category()), bodyj);
            //     }
            // }
            let reversed_hash_str: Vec<String> = self
                .client
                .post(reverse_url)
                // .multipart(form)
                .send()
                .ok()
                .and_then(|res| res.text().ok())
                .and_then(|txt| serde_json::from_str::<Vec<String>>(txt.as_str()).ok())
                .unwrap_or(vec![]);
            let reversed_hashes = reversed_hash_str
                .iter()
                .map(|s| general_purpose::STANDARD_NO_PAD.decode(s).ok())
                .flatten()
                .collect::<Vec<Vec<u8>>>();
            let reversed_models = reversed_hashes
                .into_par_iter()
                .flat_map(|hash| {
                    // let hash_str = general_purpose::STANDARD.encode(hash);
                    if let Ok(cache_url) = self.url.join("/reversed/cache/fetch") {
                        return self
                            .client
                            .get(cache_url)
                            .body(hash)
                            .send()
                            .ok()
                            .and_then(|r| r.text().ok())
                            .and_then(|txt| OpaqueDesignModel::from_json_str(txt.as_str()).ok())
                            .map(|x| Arc::new(x) as Arc<dyn DesignModel>);
                    }
                    None
                })
                .collect();
            return reversed_models;
            // if let Some((mut ws, _)) = mut_url
            //     .socket_addrs(|| None)
            //     .ok()
            //     .and_then(|addrs| addrs.first().cloned())
            //     .and_then(|addr| {
            //         std::net::TcpStream::connect_timeout(&addr, Duration::from_millis(200)).ok()
            //     })
            //     .and_then(|stream| tungstenite::client(reverse_url, stream).ok())
            // {
            //     // send solved decision models
            //     for m in solved_decision_models {
            //         if let Ok(decision_json) = OpaqueDecisionModel::from(m).to_json() {
            //             if let Err(e) = ws.send(tungstenite::Message::text(decision_json)) {
            //                 debug!("Decision JSON upload error {}", e.to_string());
            //             }
            //         }
            //     }
            //     // same for design models
            //     for m in design_models {
            //         if let Ok(design_json) = OpaqueDesignModel::from(m.as_ref()).to_json() {
            //             if let Err(e) = ws.send(tungstenite::Message::text(design_json)) {
            //                 debug!("Design JSON upload error {}", e.to_string());
            //             };
            //         };
            //     }
            //     if let Err(e) = ws.send(tungstenite::Message::text("done")) {
            //         debug!("Failed to send 'done': {}", e.to_string());
            //     };
            //     let (identified_tx, identified_rx) =
            //         std::sync::mpsc::channel::<Arc<dyn DesignModel>>();
            //     // the way in which the things are being reversed is currently a bit hacky.
            //     // in the future it is best if the reciever does NOT depend on any time-outs.
            //     // Currently it seems like the websocket connetions are a bit janky, so we do this workaround.
            //     ws.flush().expect("Failed to flush info for reversing");
            //     let imodule_name = self.unique_identifier().to_owned();
            //     std::thread::spawn(move || {
            //         while let Ok(message) = ws.read() {
            //             // besides the answer, also read the module's messages
            //             match message {
            //                 tungstenite::Message::Text(txt_msg) => {
            //                     if txt_msg.eq_ignore_ascii_case("done") {
            //                         debug!("Reverse done for {}", imodule_name);
            //                         break;
            //                     } else if let Ok(opaque) =
            //                         OpaqueDesignModel::from_json_str(txt_msg.as_str())
            //                     {
            //                         let opaquea = Arc::new(opaque) as Arc<dyn DesignModel>;
            //                         if let Err(e) = identified_tx.send(opaquea) {
            //                             debug!(
            //                                 "Failed to recover an identified design model with: {}",
            //                                 e.to_string()
            //                             );
            //                         };
            //                     }
            //                 }
            //                 tungstenite::Message::Binary(decision_cbor) => {
            //                     if let Ok(opaque) =
            //                         OpaqueDesignModel::from_cbor(decision_cbor.as_slice())
            //                     {
            //                         let opaquea = Arc::new(opaque) as Arc<dyn DesignModel>;
            //                         if let Err(e) = identified_tx.send(opaquea) {
            //                             debug!(
            //                                 "Failed to recover an identified design model with: {}",
            //                                 e.to_string()
            //                             );
            //                         };
            //                     }
            //                 }
            //                 tungstenite::Message::Ping(_) => {
            //                     if let Err(_) = ws.send(tungstenite::Message::Pong(vec![])) {
            //                         debug!(
            //                             "Failed to send ping message to other end. Trying to proceed anyway."
            //                         );
            //                     };
            //                 }
            //                 tungstenite::Message::Pong(_) => {
            //                     if let Err(_) = ws.send(tungstenite::Message::Ping(vec![])) {
            //                         debug!(
            //                             "Failed to send pong message to other end. Trying to proceed anyway."
            //                         );
            //                     };
            //                 }
            //                 _ => break,
            //             }
            //         }
            //     });
            //     let mut reverse_identified = Vec::new();
            //     // println!("Reverse done for {}", self.unique_identifier());
            //     while let Ok(m) = identified_rx.recv_timeout(Duration::from_millis(
            //         250 * ((solved_decision_models.len() + design_models.len()) as u64),
            //     )) {
            //         reverse_identified.push(m);
            //     }
            //     // get all last without blocking
            //     identified_rx
            //         .try_iter()
            //         .for_each(|m| reverse_identified.push(m));
            //     return reverse_identified;
            // }
        }
        vec![]
    }

    fn explorers(&self) -> Vec<Arc<dyn idesyde_core::Explorer>> {
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

pub fn find_modules(modules_path: &Path) -> Vec<Arc<dyn Module>> {
    let mut imodules: Vec<Arc<dyn Module>> = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        let jar_modules: Vec<PathBuf> = read_dir
            .filter_map(|e| e.ok())
            .map(|e| e.path())
            .filter(|p| p.is_file())
            .map(|p| p.read_link().unwrap_or(p))
            .filter(|p| {
                p.extension()
                    .map(|ext| ext.eq_ignore_ascii_case("jar"))
                    .unwrap_or(false)
            })
            .collect();
        imodules.extend(
            java_modules_from_jar_paths(jar_modules.as_slice())
                .into_iter()
                .map(|x| Arc::new(x) as Arc<dyn Module>),
        );
        // let prepared: Vec<Arc<dyn Module>> = read_dir
        //     .par_bridge()
        //     .into_par_iter()
        //     .flat_map(|e| {
        //         if let Ok(de) = e {
        //             let p = de.path();
        //             if p.is_file() {
        //                 let prog = p.read_link().unwrap_or(p);
        //                 if let Some(imodule) = ExternalServerModule::try_create_local(prog.clone())
        //                 {
        //                     return Some(Arc::new(imodule) as Arc<dyn Module>);
        //                 }
        //                 //  else {
        //                 //     return Some(Arc::new(ExternalIdentificationModule {
        //                 //         command_path_: prog.clone(),
        //                 //         identified_path_: identified_path.to_path_buf(),
        //                 //         inputs_path_: inputs_path.to_path_buf(),
        //                 //         solved_path_: solved_path.to_path_buf(),
        //                 //         reverse_path_: integration_path.to_path_buf(),
        //                 //         output_path_: output_path.to_path_buf(),
        //                 //     })
        //                 //         as Arc<dyn IdentificationModule>);
        //                 // }
        //             }
        //         }
        //         None
        //     })
        //     .collect();
        // imodules.extend(prepared.into_iter());
    }
    imodules
}

// pub fn find_exploration_modules(modules_path: &Path) -> Vec<Arc<dyn Module>> {
//     let mut emodules: Vec<Arc<dyn Module>> = Vec::new();
//     if let Ok(read_dir) = modules_path.read_dir() {
//         for e in read_dir {
//             if let Ok(de) = e {
//                 let p = de.path();
//                 if p.is_file() {
//                     let prog = p.read_link().unwrap_or(p);
//                     if let Some(emodule) =
//                         ExternalServerExplorationModule::try_create_local(prog.clone())
//                     {
//                         emodules.push(Arc::new(emodule));
//                     }
//                 }
//             }
//         }
//     }
//     emodules
// }

pub fn compute_dominant_decision_models<'a>(
    decision_models: &'a Vec<&'a Arc<dyn DecisionModel>>,
) -> Vec<&'a Arc<dyn DecisionModel>> {
    decision_models
        .into_iter()
        .filter(|m| {
            decision_models.iter().all(|o| match m.partial_cmp(&o) {
                Some(Ordering::Greater) | Some(Ordering::Equal) | None => true,
                _ => false,
            })
        })
        .map(|o| o.to_owned())
        .collect()
}
