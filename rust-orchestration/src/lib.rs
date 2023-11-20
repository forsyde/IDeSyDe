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

use exploration::ExternalExplorerBuilder;

use identification::ExternalServerIdentifiticationIterator;
use idesyde_core::DecisionModel;
use idesyde_core::DesignModel;
use idesyde_core::Explorer;
use idesyde_core::Module;

use idesyde_core::OpaqueDecisionModel;
use idesyde_core::OpaqueDesignModel;
use log::debug;
use log::warn;
use rayon::prelude::*;
use url::Url;

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

    fn start_identification(
        &self,
        initial_design_models: &Vec<Arc<dyn DesignModel>>,
        initial_decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Box<dyn idesyde_core::IdentificationIterator> {
        let mut mut_url = self.url.clone();
        mut_url
            .set_scheme("ws")
            .expect("Failed to set scheme to 'ws'.");
        if let Ok(identify_url) = mut_url.join("/identify") {
            if let Some((ws, _)) = mut_url
                .socket_addrs(|| None)
                .ok()
                .and_then(|addrs| addrs.first().cloned())
                .and_then(|addr| std::net::TcpStream::connect(addr).ok())
                .and_then(|stream| tungstenite::client(identify_url, stream).ok())
            {
                return Box::new(ExternalServerIdentifiticationIterator::new(
                    initial_design_models,
                    initial_decision_models,
                    ws,
                ));
            }
        }
        Box::new(idesyde_core::empty_identification_iter())
    }

    fn reverse_identification(
        &self,
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> Box<dyn Iterator<Item = Arc<dyn DesignModel>>> {
        // let mut integrated: Vec<Box<dyn DesignModel>> = Vec::new();
        // send decision models
        solved_decision_models.par_iter().for_each(|m| {
            if let Ok(mut decision_url) = self.url.join("/explored") {
                decision_url.set_query(Some("session=0"));
                let opaque = OpaqueDecisionModel::from(m);
                let mut form = reqwest::blocking::multipart::Form::new();
                if let Ok(cbor_body) = opaque.to_cbor::<Vec<u8>>() {
                    form = form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
                } else if let Ok(json_body) = opaque.to_json() {
                    form = form.text("json", json_body);
                }
                if let Err(e) = self.client.put(decision_url).multipart(form).send() {
                    warn!("Failed to send explored decision model to module {}. Trying to proceed anyway.", self.unique_identifier());
                    debug!("Message was: {}", e.to_string());
                };
            };
        });
        // same for design models
        design_models.par_iter().for_each(|m| {
            if let Ok(mut design_url) = self.url.join("/design") {
                design_url.set_query(Some("session=0"));
                let mut form = reqwest::blocking::multipart::Form::new();
                let opaque = OpaqueDesignModel::from(m.as_ref());
                if let Ok(cbor_body) = opaque.to_cbor() {
                    form = form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
                } else if let Ok(json_body) = opaque.to_json() {
                    form = form.text("json", json_body);
                }
                if let Err(e) = self.client.put(design_url).multipart(form).send() {
                    warn!("Failed to send explored decision model to module {}. Trying to proceed anyway.", self.unique_identifier());
                    debug!("Message was: {}", e.to_string());
                };
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
                std::iter::repeat_with(move || {
                    client
                        .get(reversed_url.clone())
                        .query(&[("session", "0", "encoding", "cbor")])
                        .send()
                        .ok()
                        .filter(|r| !r.status().as_str().eq_ignore_ascii_case("204"))
                        .and_then(|r| r.bytes().ok())
                        .map(|b| b.to_vec())
                        .and_then(|v| OpaqueDesignModel::from_cbor(v.as_slice()).ok())
                        .or_else(|| {
                            client
                                .get(reversed_url.clone())
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
        let prepared: Vec<Arc<dyn Module>> = read_dir
            .par_bridge()
            .into_par_iter()
            .flat_map(|e| {
                if let Ok(de) = e {
                    let p = de.path();
                    if p.is_file() {
                        let prog = p.read_link().unwrap_or(p);
                        if let Some(imodule) = ExternalServerModule::try_create_local(prog.clone())
                        {
                            return Some(Arc::new(imodule) as Arc<dyn Module>);
                        }
                        //  else {
                        //     return Some(Arc::new(ExternalIdentificationModule {
                        //         command_path_: prog.clone(),
                        //         identified_path_: identified_path.to_path_buf(),
                        //         inputs_path_: inputs_path.to_path_buf(),
                        //         solved_path_: solved_path.to_path_buf(),
                        //         reverse_path_: integration_path.to_path_buf(),
                        //         output_path_: output_path.to_path_buf(),
                        //     })
                        //         as Arc<dyn IdentificationModule>);
                        // }
                    }
                }
                None
            })
            .collect();
        imodules.extend(prepared.into_iter());
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
