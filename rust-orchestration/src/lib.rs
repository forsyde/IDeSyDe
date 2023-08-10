use std::cmp::Ordering;
use std::collections::HashMap;

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

use idesyde_blueprints::DecisionModelMessage;
use idesyde_core::headers::load_decision_model_header_from_path;
use idesyde_core::headers::load_decision_model_headers_from_binary;

use idesyde_core::headers::DecisionModelHeader;
use idesyde_core::headers::DesignModelHeader;
use idesyde_core::headers::ExplorationBid;

use idesyde_core::DecisionModel;
use idesyde_core::DesignModel;
use idesyde_core::ExplorationModule;
use idesyde_core::IdentificationModule;
use log::debug;
use log::warn;

use rayon::prelude::*;

#[derive(Eq, Clone)]
pub struct OpaqueDecisionModel {
    header: DecisionModelHeader,
    body_json: Option<String>,
    body_msgpack: Option<Vec<u8>>,
    body_cbor: Option<Vec<u8>>,
}

impl OpaqueDecisionModel {
    pub fn from_json_str(header: &DecisionModelHeader, s: &str) -> OpaqueDecisionModel {
        OpaqueDecisionModel {
            header: header.to_owned(),
            body_json: Some(s.to_string()),
            body_msgpack: None,
            body_cbor: None,
        }
    }

    pub fn from_decision_message(message: &DecisionModelMessage) -> OpaqueDecisionModel {
        OpaqueDecisionModel {
            header: message.header().to_owned(),
            body_json: message.body_with_newlines_unescaped().to_owned(),
            body_msgpack: None,
            body_cbor: None,
        }
    }

    pub fn from_header(header: &DecisionModelHeader) -> OpaqueDecisionModel {
        OpaqueDecisionModel {
            header: header.to_owned(),
            body_json: header
                .body_path
                .to_owned()
                .and_then(|x| std::fs::read_to_string(x).ok())
                .and_then(|x| serde_json::from_str(&x).ok()),
            body_msgpack: None,
            body_cbor: None,
        }
    }
}

impl DecisionModel for OpaqueDecisionModel {
    fn category(&self) -> String {
        self.header.category.to_owned()
    }

    fn header(&self) -> DecisionModelHeader {
        self.header.to_owned()
    }

    fn body_as_json(&self) -> Option<String> {
        self.body_json.to_owned()
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        self.body_msgpack.to_owned()
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        self.body_cbor.to_owned()
    }
}

impl PartialEq for OpaqueDecisionModel {
    fn eq(&self, other: &Self) -> bool {
        self.header == other.header && self.body_as_json() == other.body_as_json()
    }
}

trait LocalServerLike {
    fn get_process(&self) -> Arc<Mutex<Child>>;

    fn write_line_to_input(&self, s: &str) -> Option<()> {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(childin) = &mut server.stdin {
                let mut buf = BufWriter::new(childin);
                return writeln!(buf, "{}", s).ok();
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

    fn read_all_err(&self) -> Option<String> {
        if let Ok(mut server_guard) = self.get_process().lock() {
            let server = server_guard.deref_mut();
            if let Some(out) = &mut server.stderr {
                let buf = BufReader::new(out);
                return buf.lines().flatten().reduce(|s1, s2| s1 + &s2);
            }
        }
        None
    }
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

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalIdentificationModule {
    command_path_: PathBuf,
    inputs_path_: PathBuf,
    identified_path_: PathBuf,
    solved_path_: PathBuf,
    reverse_path_: PathBuf,
    output_path_: PathBuf,
}

impl ExternalIdentificationModule {
    pub fn is_java(&self) -> bool {
        self.command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false)
    }
}

impl IdentificationModule for ExternalIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn identification_step(
        &self,
        iteration: i32,
        _design_models: &Vec<Box<dyn DesignModel>>,
        _decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Vec<Arc<dyn DecisionModel>> {
        let is_java = self
            .command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let output = match is_java {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(&self.command_path_)
                .arg("-m")
                .arg(&self.inputs_path_)
                .arg("-i")
                .arg(&self.identified_path_)
                .arg("-t")
                .arg(iteration.to_string())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            false => std::process::Command::new(&self.command_path_)
                .arg("-m")
                .arg(&self.inputs_path_)
                .arg("-i")
                .arg(&self.identified_path_)
                .arg("-t")
                .arg(iteration.to_string())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
        };
        if let Ok(out) = output {
            if let Ok(s) = String::from_utf8(out.stdout) {
                let identified: Vec<Arc<dyn DecisionModel>> = s
                    .lines()
                    .flat_map(|p| {
                        if let Ok(b) = std::fs::read(p) {
                            if let Ok(header) = rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()) {
                                return Some(Arc::new(header) as Arc<dyn DecisionModel>);
                            } else {
                                warn!("Failed to deserialize header coming from {}. Check this module for correctness.", self.unique_identifier());
                            }
                        } else {
                            warn!("Unexpected header file from module {}. Check this module for correctness.", self.unique_identifier())
                        }
                        None
                    })
                    .collect();
                return identified;
            }
        }
        Vec::new()
    }

    fn reverse_identification(
        &self,
        _decision_model: &Vec<Arc<dyn DecisionModel>>,
        _design_model: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>> {
        let is_java = self
            .command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let output = match is_java {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(&self.command_path_)
                .arg("-m")
                .arg(&self.inputs_path_)
                .arg("-s")
                .arg(&self.solved_path_)
                .arg("-r")
                .arg(&self.reverse_path_)
                .arg("-o")
                .arg(&self.output_path_)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            false => std::process::Command::new(&self.command_path_)
                .arg("-m")
                .arg(&self.inputs_path_)
                .arg("-s")
                .arg(&self.solved_path_)
                .arg("-r")
                .arg(&self.reverse_path_)
                .arg("-o")
                .arg(&self.output_path_)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
        };
        if let Ok(out) = output {
            if let Ok(s) = String::from_utf8(out.stdout) {
                let reversed: Vec<Box<dyn DesignModel>> = s
                    .lines()
                    .map(|p| {
                        let b = std::fs::read(p)
                            .expect("Failed to read header file from disk during identification");
                        let header = rmp_serde::from_slice::<DesignModelHeader>(b.as_slice())
                            .expect(
                            "Failed to deserialize header file from disk during identification.",
                        );
                        Box::new(header) as Box<dyn DesignModel>
                    })
                    .collect();
                return reversed;
            }
        }
        Vec::new()
    }
}

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalExplorationModule {
    command_path_: PathBuf,
    identified_path_: PathBuf,
    solved_path_: PathBuf,
}

impl ExternalExplorationModule {
    pub fn is_java(&self) -> bool {
        self.command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false)
    }
}

impl ExplorationModule for ExternalExplorationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    // fn available_criterias(
    //     &self,
    //     _m: Box<dyn idesyde_core::DecisionModel>,
    // ) -> std::collections::HashMap<String, f32> {
    //     HashMap::new() // TODO: put interfaces later
    // }

    fn bid(&self, m: Arc<dyn idesyde_core::DecisionModel>) -> Vec<ExplorationBid> {
        let headers = load_decision_model_headers_from_binary(&self.identified_path_);
        let chosen_path = headers
            .iter()
            .find(|(_, h)| h == &m.header())
            .map(|(p, _)| p)
            .unwrap();
        let output = match self.is_java() {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(&self.command_path_)
                .arg("-c")
                .arg(chosen_path)
                .arg("-i")
                .arg(&self.identified_path_)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            false => std::process::Command::new(&self.command_path_)
                .arg("-c")
                .arg(chosen_path)
                .arg("-i")
                .arg(&self.identified_path_)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
        };
        let o = output
            .expect("Failed to get combination from exploration module.")
            .stdout;
        o.lines().flat_map(|l_or_err| l_or_err.ok()).map(|l| {
            match serde_json::from_str(l.as_str()) {
                Ok(bid) => bid,
                Err(e) => {
                    warn!("Failed to deserialize combination from exploration module. Assuming it cannot explore.");
                    debug!("Given error is: {}", e.to_string());
                    debug!(
                        "Return output from the exploration module is: {}",
                        std::str::from_utf8(&o).unwrap_or("NOT UTF8")
                    );
                    ExplorationBid {
                        explorer_unique_identifier: self.unique_identifier(),
                        decision_model_category: m.category(),
                        can_explore: false,
                        properties: HashMap::new(),
                    }
                }
            }
        }).collect()
    }

    fn explore(
        &self,
        m: Arc<dyn idesyde_core::DecisionModel>,
        explorer_id: &str,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>> {
        let headers = load_decision_model_headers_from_binary(&self.identified_path_);
        let chosen_path = headers
            .iter()
            .find(|(_, h)| h == &m.header())
            .map(|(p, _)| p)
            .unwrap();
        let child_opt = match self.is_java() {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(&self.command_path_)
                .arg("-e")
                .arg(chosen_path)
                .arg("-i")
                .arg(&self.identified_path_)
                .arg("-o")
                .arg(&self.solved_path_)
                .arg("-n")
                .arg(explorer_id.to_string())
                .arg("--maximum-solutions")
                .arg(format!("{}", max_sols))
                .arg("--total-timeout")
                .arg(format!("{}", total_timeout))
                .arg("--time-resolution")
                .arg(format!("{}", time_resolution))
                .arg("--memory-resolution")
                .arg(format!("{}", memory_resolution))
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
            false => std::process::Command::new(&self.command_path_)
                .arg("-e")
                .arg(chosen_path)
                .arg("-i")
                .arg(&self.identified_path_)
                .arg("-o")
                .arg(&self.solved_path_)
                .arg("-n")
                .arg(explorer_id.to_string())
                .arg("--maximum-solutions")
                .arg(format!("{}", max_sols))
                .arg("--total-timeout")
                .arg(format!("{}", total_timeout))
                .arg("--time-resolution")
                .arg(format!("{}", time_resolution))
                .arg("--memory-resolution")
                .arg(format!("{}", memory_resolution))
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
        };
        let uid = self.unique_identifier().clone();
        let child = child_opt.expect("Failed to initiate explorer");
        let out = child.stdout.expect("Failed to acquire explorer STDOUT");
        let err = child.stderr.expect("Failed to achique explorer STDERR");
        if BufReader::new(err).lines().flatten().any(|l| !l.is_empty()) {
            warn!(
                "Exploration module {} produced error messages. Please check it for correctness.",
                uid
            );
        }
        let buf = BufReader::new(out);
        Box::new(
            buf.lines()
                .map(|l| l.expect("Failed to read solution during exploration"))
                .flat_map(move |f| {
                    let h = load_decision_model_header_from_path(Path::new(f.as_str()));
                    if h.is_none() {
                        warn!("Exploration module {} produced non-compliant output '{}' during exploration. Please check it for correctness.", uid, f);
                    }
                    h
                })
                .map(|m| Arc::new(m) as Arc<dyn DecisionModel>),
        )
    }
}

pub struct ExternalServerExplorationModule {
    name: String,
    identified_path: PathBuf,
    solved_path: PathBuf,
    process: Arc<Mutex<Child>>,
}

impl ExternalServerExplorationModule {
    pub fn try_create_local(
        command_path_: PathBuf,
        identified_path_: PathBuf,
        solved_path_: PathBuf,
    ) -> Option<ExternalServerExplorationModule> {
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
                .arg("stdio")
                .arg("-i")
                .arg(&identified_path_)
                .arg("-o")
                .arg(&solved_path_)
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
            false => std::process::Command::new(&command_path_)
                .arg("--server")
                .arg("stdio")
                .arg("-i")
                .arg(&identified_path_)
                .arg("-o")
                .arg(&solved_path_)
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
        };
        // the test involves just exitting it
        if let Ok(mut server_child) = child_res {
            if let Some(childout) = &mut server_child.stdout {
                let mut line = "".to_string();
                let mut buf = BufReader::new(childout);
                buf.read_line(&mut line)
                    .expect("Could not read initialization line for imodule.");
                if line.contains("INITIALIZED") {
                    return Some(ExternalServerExplorationModule {
                        name: command_path_
                            .clone()
                            .file_name()
                            .and_then(|x| x.to_str())
                            .and_then(|x| x.split('.').next())
                            .expect("Could not fetch name from imodule file name.")
                            .to_string(),
                        identified_path: identified_path_.to_path_buf(),
                        solved_path: solved_path_.to_path_buf(),
                        process: Arc::new(Mutex::new(server_child)),
                    });
                }
            }
        }
        None
    }
}

impl LocalServerLike for ExternalServerExplorationModule {
    fn get_process(&self) -> Arc<Mutex<Child>> {
        self.process.clone()
    }
}

impl Drop for ExternalServerExplorationModule {
    fn drop(&mut self) {
        if let Ok(mut server_guard) = self.process.lock() {
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

impl ExplorationModule for ExternalServerExplorationModule {
    fn unique_identifier(&self) -> String {
        self.name.to_owned()
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> Vec<ExplorationBid> {
        if let Some(()) = self.write_line_to_input(
            format!(
                "BID {}",
                DecisionModelMessage::from_dyn_decision_model(m.as_ref()).to_json_str()
            )
            .as_str(),
        ) {
            return self
                .map_output(|buf| {
                    buf.lines()
                        .flatten()
                        .map(|line| {
                            if line.starts_with("RESULT") {
                                return ExplorationBid::from_json_str(&line[6..].trim());
                            } else if line.eq_ignore_ascii_case("FINISHED") {
                                return None;
                            }
                            None
                        })
                        .take_while(|x| x.is_some())
                        .flatten()
                        .collect()
                })
                .unwrap_or(Vec::new());
            // return std::iter::repeat_with(|| self.read_line_from_output())
            //     .flatten()
            //     .map(|line| {
            //         if line.starts_with("RESULT") {
            //             return ExplorationBid::from_json_str(&line[6..].trim());
            //         } else if line.eq_ignore_ascii_case("FINISHED") {
            //             return None;
            //         }
            //         None
            //     })
            //     .take_while(|x| x.is_some())
            //     .flatten()
            //     .collect();
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
    ) -> Box<dyn Iterator<Item = Arc<dyn DecisionModel>>> {
        self.write_line_to_input(
            format!("SET identified-path {}", self.identified_path.display()).as_str(),
        );
        self.write_line_to_input(
            format!("SET solved-path {}", self.solved_path.display()).as_str(),
        );
        self.write_line_to_input(format!("SET max-sols {}", max_sols).as_str());
        self.write_line_to_input(format!("SET total-timeout {}", total_timeout).as_str());
        self.write_line_to_input(format!("SET time-resolution {}", time_resolution).as_str());
        self.write_line_to_input(format!("SET memory-resolution {}", memory_resolution).as_str());
        self.write_line_to_input(
            format!(
                "EXPLORE {} {}",
                explorer_id,
                DecisionModelMessage::from_dyn_decision_model(m.as_ref()).to_json_str()
            )
            .as_str(),
        );
        let explored: Vec<Arc<dyn DecisionModel>> = self
            .map_output(|buf| {
                buf.lines()
                    .flatten()
                    .map(|line| {
                        if line.contains("RESULT") {
                            let mut payload = line[6..].trim().split(" ");
                            let _objs_ = payload.next();
                            return payload
                                .next()
                                .and_then(|m_str| DecisionModelMessage::from_json_str(m_str.trim()))
                                .map(|x| OpaqueDecisionModel::from_decision_message(&x))
                                .map(|m| Arc::new(m) as Arc<dyn DecisionModel>);
                        } else if line.contains("FINISHED") {
                            return None;
                        }
                        None
                    })
                    .take_while(|x| x.is_some())
                    .flatten()
                    .collect()
            })
            .unwrap_or(Vec::new());
        // let explored: Vec<Arc<dyn DecisionModel>> =
        //     std::iter::repeat_with(|| self.read_line_from_output())
        //         .flatten()
        //         .map(|line| {
        //             if line.contains("RESULT") {
        //                 let mut payload = line[6..].trim().split(" ");
        //                 let _objs_ = payload.next();
        //                 return payload
        //                     .next()
        //                     .and_then(|m_str| DecisionModelMessage::from_json_str(m_str.trim()))
        //                     .map(|x| OpaqueDecisionModel::from_decision_message(&x))
        //                     .map(|m| Arc::new(m) as Arc<dyn DecisionModel>);
        //             } else if line.contains("FINISHED") {
        //                 return None;
        //             }
        //             None
        //         })
        //         .take_while(|x| x.is_some())
        //         .flatten()
        //         .collect();
        Box::new(explored.into_iter())
    }
}

pub fn find_identification_modules(
    modules_path: &Path,
    identified_path: &Path,
    inputs_path: &Path,
    solved_path: &Path,
    integration_path: &Path,
    output_path: &Path,
) -> Vec<Arc<dyn IdentificationModule>> {
    let mut imodules: Vec<Arc<dyn IdentificationModule>> = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        let prepared: Vec<Arc<dyn IdentificationModule>> = read_dir
            .par_bridge()
            .into_par_iter()
            .flat_map(|e| {
                if let Ok(de) = e {
                    let p = de.path();
                    if p.is_file() {
                        let prog = p.read_link().unwrap_or(p);
                        if let Some(imodule) = ExternalServerIdentificationModule::try_create_local(
                            prog.clone(),
                            inputs_path.to_path_buf(),
                            identified_path.to_path_buf(),
                            solved_path.to_path_buf(),
                            integration_path.to_path_buf(),
                            output_path.to_path_buf(),
                        ) {
                            return Some(Arc::new(imodule) as Arc<dyn IdentificationModule>);
                        } else {
                            return Some(Arc::new(ExternalIdentificationModule {
                                command_path_: prog.clone(),
                                identified_path_: identified_path.to_path_buf(),
                                inputs_path_: inputs_path.to_path_buf(),
                                solved_path_: solved_path.to_path_buf(),
                                reverse_path_: integration_path.to_path_buf(),
                                output_path_: output_path.to_path_buf(),
                            })
                                as Arc<dyn IdentificationModule>);
                        }
                    }
                }
                None
            })
            .collect();
        imodules.extend(prepared.into_iter());
    }
    imodules
}

pub fn find_exploration_modules(
    modules_path: &Path,
    identified_path: &Path,
    solved_path: &Path,
) -> Vec<Arc<dyn ExplorationModule>> {
    let mut emodules: Vec<Arc<dyn ExplorationModule>> = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    if let Some(emodule) = ExternalServerExplorationModule::try_create_local(
                        prog.clone(),
                        identified_path.to_path_buf(),
                        solved_path.to_path_buf(),
                    ) {
                        emodules.push(Arc::new(emodule));
                    } else {
                        emodules.push(Arc::new(ExternalExplorationModule {
                            command_path_: prog.clone(),
                            identified_path_: identified_path.to_path_buf(),
                            solved_path_: solved_path.to_path_buf(),
                        }));
                    }
                }
            }
        }
    }
    emodules
}

pub fn identification_procedure(
    imodules: &Vec<Arc<dyn IdentificationModule>>,
    design_models: &Vec<Box<dyn DesignModel>>,
    pre_identified: &Vec<Arc<dyn DecisionModel>>,
    starting_iter: i32,
) -> Vec<Arc<dyn DecisionModel>> {
    let mut step = starting_iter;
    let mut fix_point = false;
    let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
    identified.extend_from_slice(pre_identified);
    while !fix_point {
        // the step condition forces the procedure to go at least one more, fundamental for incrementability
        fix_point = true;
        let before = identified.len();
        let new_identified: Vec<Arc<dyn DecisionModel>> = imodules
            .par_iter()
            .flat_map(|imodule| imodule.identification_step(step, &design_models, &identified))
            .filter(|potential| !identified.contains(potential))
            .collect();
        // this contain check is done again because there might be imodules that identify the same decision model,
        // and since the filtering before is step-based, it would add both identical decision models.
        // This new for-if fixes this by checking every model of this step.
        for m in new_identified {
            if !identified.contains(&m) {
                identified.push(m);
            }
        }
        // identified.extend(new_identified.into_iter());
        // for imodule in imodules {
        //     let potential = imodule.identification_step(step, &design_models, &identified);
        //     // potential.retain(|m| !identified.contains(m));
        //     for m in potential {
        //         if !identified.contains(&m) {
        //             identified.push(m);
        //         }
        //     }
        // }
        debug!(
            "{} total decision models identified at step {}",
            identified.len(),
            step
        );
        fix_point = fix_point && (identified.len() == before);
        step += 1;
    }
    identified
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

#[derive(Debug)]
pub struct ExternalServerIdentificationModule {
    name: String,
    inputs_path_: PathBuf,
    identified_path_: PathBuf,
    solved_path_: PathBuf,
    reverse_path_: PathBuf,
    output_path_: PathBuf,
    process: Arc<Mutex<Child>>,
}

impl ExternalServerIdentificationModule {
    pub fn try_create_local(
        command_path_: PathBuf,
        inputs_path_: PathBuf,
        identified_path_: PathBuf,
        solved_path_: PathBuf,
        reverse_path_: PathBuf,
        output_path_: PathBuf,
    ) -> Option<ExternalServerIdentificationModule> {
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
                .arg("stdio")
                .arg("-m")
                .arg(&inputs_path_)
                .arg("-i")
                .arg(&identified_path_)
                .arg("-s")
                .arg(&solved_path_)
                .arg("-r")
                .arg(&reverse_path_)
                .arg("-o")
                .arg(&output_path_)
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
            false => std::process::Command::new(&command_path_)
                .arg("--server")
                .arg("stdio")
                .arg("-m")
                .arg(&inputs_path_)
                .arg("-i")
                .arg(&identified_path_)
                .arg("-s")
                .arg(&solved_path_)
                .arg("-r")
                .arg(&reverse_path_)
                .arg("-o")
                .arg(&output_path_)
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
        };
        // the test involves just exitting it
        if let Ok(mut server_child) = child_res {
            if let Some(childout) = &mut server_child.stdout {
                let mut line = "".to_string();
                let mut buf = BufReader::new(childout);
                buf.read_line(&mut line)
                    .expect("Could not read initialization line for imodule.");
                if line.contains("INITIALIZED") {
                    return Some(ExternalServerIdentificationModule {
                        name: command_path_
                            .clone()
                            .file_name()
                            .and_then(|x| x.to_str())
                            .and_then(|x| x.split('.').next())
                            .expect("Could not fetch name from imodule file name.")
                            .to_string(),
                        identified_path_: identified_path_.to_path_buf(),
                        inputs_path_: inputs_path_.to_path_buf(),
                        solved_path_: solved_path_.to_path_buf(),
                        reverse_path_: reverse_path_.to_path_buf(),
                        output_path_: output_path_.to_path_buf(),
                        process: Arc::new(Mutex::new(server_child)),
                    });
                }
            }
        }
        None
    }
}

impl LocalServerLike for ExternalServerIdentificationModule {
    fn get_process(&self) -> Arc<Mutex<Child>> {
        self.process.clone()
    }
}

impl PartialEq for ExternalServerIdentificationModule {
    fn eq(&self, other: &Self) -> bool {
        self.name == other.name
            && self.inputs_path_ == other.inputs_path_
            && self.identified_path_ == other.identified_path_
            && self.solved_path_ == other.solved_path_
            && self.reverse_path_ == other.reverse_path_
            && self.output_path_ == other.output_path_
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
        if let Ok(mut server_guard) = self.process.lock() {
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

impl IdentificationModule for ExternalServerIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.name.clone()
    }

    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> Vec<Arc<dyn DecisionModel>> {
        for design_model in design_models {
            for mpath in design_model.header().model_paths {
                self.write_line_to_input(format!("DESIGN {}", mpath).as_str());
            }
        }
        for decision_model in decision_models {
            let message = DecisionModelMessage::from_dyn_decision_model(decision_model.as_ref());
            // let h = decision_model.header();
            self.write_line_to_input(format!("DECISION INLINE {}", message.to_json_str()).as_str());
        }
        self.write_line_to_input(format!("IDENTIFY {}", iteration).as_str());
        self.map_output(|buf| {
            buf.lines()
                .flatten()
                .map(|line| {
                    if line.contains("DECISION INLINE") {
                        let payload = &line[15..].trim();
                        let mes = DecisionModelMessage::from_json_str(payload);
                        if let Some(message) = mes {
                            let boxed =
                                Arc::new(OpaqueDecisionModel::from_decision_message(&message))
                                    as Arc<dyn DecisionModel>;
                            return Some(boxed);
                        }
                    } else if !line.trim().eq_ignore_ascii_case("FINISHED") {
                        warn!(
                            "Ignoring non-compliant identification result by module {}: {}",
                            self.unique_identifier(),
                            line
                        );
                        debug!(
                            "module {} error: {}",
                            self.unique_identifier(),
                            self.read_all_err()
                                .unwrap_or("Unable to capture".to_string())
                        )
                    }
                    None
                })
                .take_while(|x| x.is_some())
                .flatten()
                .collect()
        })
        .unwrap_or(Vec::new())
        // if let Ok(mut server_guard) = self.get_process().lock() {
        //     let server = server_guard.deref_mut();
        //     if let Some(out) = &mut server.stdout {
        //         return BufReader::new(out)
        //             .lines()
        //             .flatten()
        //             .map(|line| {
        //                 if line.contains("DECISION INLINE") {
        //                     let payload = &line[15..].trim();
        //                     let mes = DecisionModelMessage::from_json_str(payload);
        //                     if let Some(message) = mes {
        //                         let boxed =
        //                             Arc::new(OpaqueDecisionModel::from_decision_message(&message))
        //                                 as Arc<dyn DecisionModel>;
        //                         return Some(boxed);
        //                     }
        //                 } else if !line.trim().eq_ignore_ascii_case("FINISHED") {
        //                     warn!(
        //                         "Ignoring non-compliant identification result by module {}: {}",
        //                         self.unique_identifier(),
        //                         line
        //                     );
        //                     debug!(
        //                         "module {} error: {}",
        //                         self.unique_identifier(),
        //                         self.read_all_err()
        //                             .unwrap_or("Unable to capture".to_string())
        //                     )
        //                 }
        //                 None
        //             })
        //             .take_while(|x| x.is_some())
        //             .flatten()
        //             .collect();
        //     }
        // }
        // Vec::new()
        // return std::iter::repeat_with(|| self.read_line_from_output())
        //     .inspect(|line| print!("{}", line.to_owned().unwrap_or("Shit is empty".to_string())))
        //     .flatten()
        //     .map(|line| {
        //         if line.contains("DECISION INLINE") {
        //             let payload = &line[15..].trim();
        //             let mes = DecisionModelMessage::from_json_str(payload);
        //             if let Some(message) = mes {
        //                 let boxed = Arc::new(OpaqueDecisionModel::from_decision_message(&message))
        //                     as Arc<dyn DecisionModel>;
        //                 return Some(boxed);
        //             }
        //         } else if !line.trim().eq_ignore_ascii_case("FINISHED") {
        //             warn!(
        //                 "Ignoring non-compliant identification result by module {}: {}",
        //                 self.unique_identifier(),
        //                 line
        //             );
        //             debug!(
        //                 "module {} error: {}",
        //                 self.unique_identifier(),
        //                 self.read_all_err()
        //                     .unwrap_or("Unable to capture".to_string())
        //             )
        //         }
        //         None
        //     })
        //     .inspect(|x| println!("result is {}", x.is_some()))
        //     .take_while(|x| x.is_some())
        //     .flatten()
        //     .collect();
    }

    fn reverse_identification(
        &self,
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>> {
        // let mut integrated: Vec<Box<dyn DesignModel>> = Vec::new();
        // save decision models and design models and ask the module to read them
        for design_model in design_models {
            for mpath in design_model.header().model_paths {
                self.write_line_to_input(format!("DESIGN {}", mpath).as_str());
            }
        }
        for decision_model in solved_decision_models {
            let message = DecisionModelMessage::from_dyn_decision_model(decision_model.as_ref());
            self.write_line_to_input(format!("SOLVED INLINE {}", message.to_json_str()).as_str());
        }
        self.write_line_to_input("INTEGRATE");
        return std::iter::repeat_with(|| self.read_line_from_output())
            .flatten()
            .map(|line| {
                if line.contains("DESIGN") {
                    let payload = &line[6..].trim();
                    let h = DesignModelHeader::from_file(std::path::Path::new(payload));
                    if let Some(header) = h {
                        let boxed = Box::new(header) as Box<dyn DesignModel>;
                        return Some(boxed);
                    }
                } else if !line.trim().eq_ignore_ascii_case("FINISHED") {
                    warn!(
                        "Ignoring non-compliant integration result by module {}: {}",
                        self.unique_identifier(),
                        line
                    );
                }
                None
            })
            .take_while(|x| x.is_some())
            .flatten()
            .collect();
        // if let Some(integrated_line) = self.read_line_from_output() {
        //     let num_models = integrated_line[10..].trim().parse().ok().unwrap_or(0usize);
        //     for _ in 0..num_models {
        //         if let Some(design_model_line) = self.read_line_from_output() {
        //             if design_model_line.contains("DESIGN") {
        //                 let payload = &design_model_line[6..].trim();
        //                 let h = DesignModelHeader::from_file(std::path::Path::new(payload));
        //                 if let Some(header) = h {
        //                     let boxed = Box::new(header) as Box<dyn DesignModel>;
        //                     if !integrated.contains(&boxed) {
        //                         integrated.push(boxed);
        //                     }
        //                 }
        //             } else {
        //                 warn!(
        //                     "Ignoring non-compliant identification result by module {}: {}",
        //                     self.unique_identifier(),
        //                     design_model_line
        //                 )
        //             }
        //         }
        //     }
        // }
        // integrated
    }
}
