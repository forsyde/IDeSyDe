use std::{
    collections::HashMap,
    io::BufRead,
    io::BufReader,
    path::{Path, PathBuf},
    process::{Child, Stdio},
    sync::{Arc, Mutex},
};

use idesyde_blueprints::{DecisionModelMessage, ExplorationSolutionMessage};
use idesyde_core::{
    headers::{
        load_decision_model_header_from_path, load_decision_model_headers_from_binary,
        ExplorationBid,
    },
    DecisionModel, ExplorationModule, ExplorationSolution,
};
use log::{debug, warn};

use crate::{models::OpaqueDecisionModel, LocalServerLike};

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
                                .map(|x| OpaqueDecisionModel::from(&x))
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
        Box::new(explored.into_iter())
    }

    fn iter_explore(
        &self,
        m: Arc<dyn DecisionModel>,
        explorer_id: &str,
        _currrent_solutions: Vec<idesyde_core::ExplorationSolution>,
        exploration_configuration: idesyde_core::ExplorationConfiguration,
        solution_iter: fn(ExplorationSolution) -> (),
    ) -> Vec<idesyde_core::ExplorationSolution> {
        self.write_line_to_input(
            format!("SET identified-path {}", self.identified_path.display()).as_str(),
        );
        self.write_line_to_input(
            format!("SET solved-path {}", self.solved_path.display()).as_str(),
        );
        self.write_line_to_input(
            format!("SET max-sols {}", exploration_configuration.max_sols).as_str(),
        );
        self.write_line_to_input(
            format!(
                "SET total-timeout {}",
                exploration_configuration.total_timeout
            )
            .as_str(),
        );
        self.write_line_to_input(
            format!(
                "SET time-resolution {}",
                exploration_configuration.time_resolution
            )
            .as_str(),
        );
        self.write_line_to_input(
            format!(
                "SET memory-resolution {}",
                exploration_configuration.memory_resolution
            )
            .as_str(),
        );
        self.write_line_to_input(
            format!(
                "EXPLORE {} {}",
                explorer_id,
                DecisionModelMessage::from_dyn_decision_model(m.as_ref()).to_json_str()
            )
            .as_str(),
        );
        self.map_output(|buf| {
            buf.lines()
                .flatten()
                .map(|line| {
                    if line.contains("RESULT") {
                        let payload = line[6..].trim();
                        if let Some(message) = ExplorationSolutionMessage::from_json_str(payload) {
                            let solution = (
                                Arc::new(OpaqueDecisionModel::from(&message.solved))
                                    as Arc<dyn DecisionModel>,
                                message.objectives.to_owned(),
                            );
                            solution_iter(solution.clone());
                            return Some(solution);
                        }
                    } else if line.contains("FINISHED") {
                        return None;
                    }
                    None
                })
                .take_while(|x| x.is_some())
                .flatten()
                .collect()
        })
        .unwrap_or(Vec::new())
    }
}

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalExplorationModule {
    pub command_path_: PathBuf,
    pub identified_path_: PathBuf,
    pub solved_path_: PathBuf,
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

    fn iter_explore(
        &self,
        _m: Arc<dyn DecisionModel>,
        _explorer_id: &str,
        _currrent_solutions: Vec<ExplorationSolution>,
        _exploration_configuration: idesyde_core::ExplorationConfiguration,
        _solution_iter: fn(ExplorationSolution) -> (),
    ) -> Vec<ExplorationSolution> {
        vec![]
    }
}
