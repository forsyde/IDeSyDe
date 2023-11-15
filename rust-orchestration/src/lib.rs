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

use exploration::ExternalServerExplorationModule;

use identification::ExternalServerIdentifiticationIterator;
use idesyde_core::DecisionModel;
use idesyde_core::DesignModel;
use idesyde_core::Module;

use idesyde_core::OpaqueDecisionModel;
use idesyde_core::OpaqueDesignModel;
use log::debug;
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

trait HttpServerLike {
    // fn get_client(&self) -> Arc<reqwest::blocking::Client>;

    // fn get_url(&self) -> Url;

    // fn send_decision<T: DecisionModel + ?Sized>(&self, model: &T) -> Result<Response, Error> {
    //     let client = self.get_client();
    //     client
    //         .post(format!(
    //             "http://{}:{}/decision",
    //             self.get_url()
    //                 .host()
    //                 .unwrap_or(url::Host::parse("127.0.0.1")),
    //             self.get_port()
    //         ))
    //         .body(DecisionModelMessage::from(model).to_json_str())
    //         .send()
    // }

    // fn send_design<T: DesignModel + ?Sized>(&self, model: &T) -> Result<Response, Error> {
    //     let client = self.get_client();
    //     // println!("{}", DesignModelMessage::from(model).to_json_str());
    //     client
    //         .post(format!(
    //             "http://{}:{}/design",
    //             self.get_address(),
    //             self.get_port()
    //         ))
    //         .body(DesignModelMessage::from(model).to_json_str())
    //         .send()
    // }

    // fn send_solved_decision<T: DecisionModel + ?Sized>(
    //     &self,
    //     model: &T,
    // ) -> Result<Response, Error> {
    //     let client = self.get_client();
    //     client
    //         .post(format!(
    //             "http://{}:{}/solved",
    //             self.get_address(),
    //             self.get_port()
    //         ))
    //         .body(DecisionModelMessage::from(model).to_json_str())
    //         .send()
    // }

    // fn send_command(&self, command: &str, query: &Vec<(&str, &str)>) -> Result<Response, Error> {
    //     let client = self.get_client();
    //     client
    //         .get(format!(
    //             "http://{}:{}/{}",
    //             self.get_address(),
    //             self.get_port(),
    //             command
    //         ))
    //         .query(query)
    //         .send()
    // }

    // fn http_get<T: Serialize + ?Sized>(
    //     &self,
    //     point: &str,
    //     query: &Vec<(&str, &str)>,
    //     body: &T,
    // ) -> Result<Response, Error> {
    //     let client = self.get_client();
    //     let m = serde_json::to_string(body)
    //         .expect("Failed to serialized an object that shoudl always work");
    //     client
    //         .get(format!(
    //             "http://{}:{}/{}",
    //             self.get_address(),
    //             self.get_port(),
    //             point
    //         ))
    //         .query(query)
    //         .body(m)
    //         .send()
    // }

    // fn http_post<T: Serialize + ?Sized>(
    //     &self,
    //     point: &str,
    //     query: &Vec<(&str, &str)>,
    //     body: &T,
    // ) -> Result<Response, Error> {
    //     let client = self.get_client();
    //     client
    //         .post(format!(
    //             "http://{}:{}/{}",
    //             self.get_address(),
    //             self.get_port(),
    //             point
    //         ))
    //         .query(query)
    //         .body(
    //             serde_json::to_string(body)
    //                 .expect("Failed to serialized an object that shoudl always work"),
    //         )
    //         .send()
    // }
}

// fn stream_lines_from_output<T: LocalServerLike>(module: T) -> Option<impl Iterator<Item = String>> {
//     if let Ok(mut server_guard) = module.get_process().lock() {
//         let server = server_guard.deref_mut();
//         if let Some(out) = &mut server.stdout {
//             let it = BufReader::new(out).lines().flatten();
//             return Some(it);
//         }
//     }
//     None
// }

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
                let port_opt = buf
                    .lines()
                    .flatten()
                    .filter(|l| l.starts_with("INITIALIZED"))
                    .map(|l| l[11..].trim().parse::<usize>())
                    .flatten()
                    .next();
                if let Some(port) = port_opt {
                    return Some(ExternalServerModule {
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

    pub fn from(name: &str, url: &Url) -> ExternalServerModule {
        ExternalServerModule {
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
        initial_design_models: &HashSet<Arc<dyn DesignModel>>,
        initial_decision_models: &HashSet<Arc<dyn DecisionModel>>,
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
                    form = form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
                } else if let Ok(json_body) = opaque.to_json() {
                    form = form.text("json", json_body);
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
                    form = form.part("cbor", reqwest::blocking::multipart::Part::bytes(cbor_body));
                } else if let Ok(json_body) = opaque.to_json() {
                    form = form.text("json", json_body);
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
}

pub fn find_modules(
    modules_path: &Path,
    identified_path: &Path,
    inputs_path: &Path,
    solved_path: &Path,
    integration_path: &Path,
    output_path: &Path,
) -> HashSet<Arc<dyn Module>> {
    let mut imodules: HashSet<Arc<dyn Module>> = HashSet::new();
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

pub fn find_exploration_modules(modules_path: &Path) -> HashSet<Arc<dyn Module>> {
    let mut emodules: HashSet<Arc<dyn Module>> = HashSet::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    if let Some(emodule) =
                        ExternalServerExplorationModule::try_create_local(prog.clone())
                    {
                        emodules.insert(Arc::new(emodule));
                    }
                }
            }
        }
    }
    emodules
}

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
