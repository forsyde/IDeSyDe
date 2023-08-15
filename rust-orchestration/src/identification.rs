use std::{
    collections::HashSet,
    hash::Hash,
    io::BufRead,
    io::BufReader,
    net::Ipv4Addr,
    path::PathBuf,
    process::{Child, Stdio},
    sync::{Arc, Mutex},
};

use rayon::prelude::*;

use idesyde_blueprints::{DecisionModelMessage, DesignModelMessage, IdentificationResultMessage};
use idesyde_core::{
    headers::{DecisionModelHeader, DesignModelHeader},
    DecisionModel, DesignModel, IdentificationModule, IdentificationResult,
};
use log::{debug, warn};

use crate::{
    models::{OpaqueDecisionModel, OpaqueDesignModel},
    HttpServerLike,
};

#[derive(Debug)]
pub struct ExternalServerIdentificationModule {
    name: String,
    address: std::net::IpAddr,
    port: usize,
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
                        address: std::net::IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1)),
                        port: port,
                        client: Arc::new(reqwest::blocking::Client::new()),
                        process: Some(Arc::new(Mutex::new(server_child))),
                    });
                }
                // if line.contains("INITIALIZED") {
                //     return Some(ExternalServerIdentificationModule {
                //         name: command_path_
                //             .clone()
                //             .file_name()
                //             .and_then(|x| x.to_str())
                //             .and_then(|x| x.split('.').next())
                //             .expect("Could not fetch name from imodule file name.")
                //             .to_string(),
                //         identified_path: identified_path_.to_path_buf(),
                //         inputs_path: inputs_path_.to_path_buf(),
                //         solved_path: solved_path_.to_path_buf(),
                //         reverse_path: reverse_path_.to_path_buf(),
                //         output_path_: output_path_.to_path_buf(),
                //         process: Arc::new(Mutex::new(server_child)),
                //     });
                // }
            }
        }
        None
    }
}

// impl LocalServerLike for ExternalServerIdentificationModule {
//     fn get_process(&self) -> Arc<Mutex<Child>> {
//         self.process.clone()
//     }
// }

impl PartialEq for ExternalServerIdentificationModule {
    fn eq(&self, other: &Self) -> bool {
        self.name == other.name && self.address == other.address && self.port == other.port
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

impl HttpServerLike for ExternalServerIdentificationModule {
    fn get_client(&self) -> Arc<reqwest::blocking::Client> {
        self.client.clone()
    }

    fn get_address(&self) -> std::net::IpAddr {
        self.address.to_owned()
    }

    fn get_port(&self) -> usize {
        self.port
    }
}

impl IdentificationModule for ExternalServerIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.name.clone()
    }

    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Arc<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> IdentificationResult {
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
        for design_model in design_models {
            // let message = DesignModelMessage::from_dyn_design_model(design_model.as_ref());
            if let Err(e) = self.send_design(design_model.as_ref()) {
                warn!(
                    "Module {} had an error while recieving a design model at {}.",
                    self.unique_identifier(),
                    e.url().map(|x| x.as_str()).unwrap_or("<MissingUrl>")
                )
            }
            // self.write_line_to_input(format!("DESIGN {}", message.to_json_str()).as_str())
            //     .expect("Error at writing");
            // println!("DESIGN {}", message.to_json_str());
        }
        for decision_model in decision_models {
            // let message = DecisionModelMessage::from_dyn_decision_model(decision_model.as_ref());
            if let Err(e) = self.send_decision(decision_model.as_ref()) {
                warn!(
                    "Module {} had an error while recieving a decision model at {}.",
                    self.unique_identifier(),
                    e.url().map(|x| x.as_str()).unwrap_or("<MissingUrl>")
                );
            }
            // let h = decision_model.header();
            // self.write_line_to_input(format!("DECISION INLINE {}", message.to_json_str()).as_str())
            //     .expect("Error at writing");
        }
        // self.write_line_to_input(format!("IDENTIFY {}", iteration).as_str());
        if let Ok(response) = self
            .send_command(
                "identify",
                &vec![("iteration", format!("{}", iteration).as_str())],
            )
            .and_then(|x| x.text())
        {
            if let Ok(v) = IdentificationResultMessage::try_from(response.as_str()) {
                return (
                    v.identified
                        .iter()
                        .map(|x| Arc::new(OpaqueDecisionModel::from(x)) as Arc<dyn DecisionModel>)
                        .collect(),
                    v.errors,
                );
            }
        };
        (vec![], HashSet::new())
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
        solved_decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> Vec<Arc<dyn DesignModel>> {
        // let mut integrated: Vec<Box<dyn DesignModel>> = Vec::new();
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
        // save decision models and design models and ask the module to read them
        for design_model in design_models {
            // let message = DesignModelMessage::from_dyn_design_model(design_model.as_ref());
            // self.write_line_to_input(format!("DESIGN {}", message.to_json_str()).as_str());
            self.send_design(design_model.as_ref());
        }
        for decision_model in solved_decision_models {
            // let message = DecisionModelMessage::from_dyn_decision_model(decision_model.as_ref());
            // self.write_line_to_input(format!("SOLVED INLINE {}", message.to_json_str()).as_str());
            self.send_solved_decision(decision_model.as_ref());
        }
        if let Ok(response) = self
            .send_command("integrate", &vec![])
            .and_then(|x| x.text())
        {
            if let Ok(v) = serde_json::from_str::<Vec<DesignModelMessage>>(response.as_str()) {
                return v
                    .iter()
                    .map(|x| Arc::new(OpaqueDesignModel::from(x)) as Arc<dyn DesignModel>)
                    .collect();
            }
        }
        vec![]
        // self.write_line_to_input("INTEGRATE");
        // self.map_output(|buf| {
        //     buf.lines()
        //         .flatten()
        //         .map(|line| {
        //             if line.contains("DESIGN") {
        //                 let payload = &line[6..].trim();
        //                 if let Some(message) = DesignModelMessage::from_json_str(&payload) {
        //                     let model = OpaqueDesignModel::from(message);
        //                     let boxed = Arc::new(model) as Arc<dyn DesignModel>;
        //                     return Some(boxed);
        //                 };
        //             } else if !line.trim().eq_ignore_ascii_case("FINISHED") {
        //                 warn!(
        //                     "Ignoring non-compliant integration result by module {}: {}",
        //                     self.unique_identifier(),
        //                     line
        //                 );
        //             }
        //             None
        //         })
        //         .take_while(|x| x.is_some())
        //         .flatten()
        //         .collect()
        // })
        // .unwrap_or(Vec::new())
    }
}

pub fn identification_procedure(
    imodules: &Vec<Arc<dyn IdentificationModule>>,
    design_models: &Vec<Arc<dyn DesignModel>>,
    pre_identified: &Vec<Arc<dyn DecisionModel>>,
    starting_iter: i32,
) -> IdentificationResult {
    let mut step = starting_iter;
    let mut fix_point = false;
    let mut identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
    let mut errors: HashSet<String> = HashSet::new();
    identified.extend_from_slice(pre_identified);
    while !fix_point {
        // the step condition forces the procedure to go at least one more, fundamental for incrementability
        fix_point = true;
        let before = identified.len();
        let (identified_step, errors_step) = imodules
            .par_iter()
            .map(|imodule| imodule.identification_step(step, &design_models, &identified))
            .reduce(
                || (vec![], HashSet::new()),
                |(m1, e1), (m2, e2)| {
                    let mut v = vec![];
                    let mut e = HashSet::new();
                    v.extend(m1);
                    v.extend(m2);
                    e.extend(e1);
                    e.extend(e2);
                    (v, e)
                },
            );
        for m in identified_step {
            if !identified.contains(&m) {
                identified.push(m);
            }
        }
        for e in errors_step {
            debug!("IRule error at step {}, {}", step, e);
            errors.insert(e);
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
    (identified, errors)
}

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalIdentificationModule {
    pub command_path_: PathBuf,
    pub inputs_path_: PathBuf,
    pub identified_path_: PathBuf,
    pub solved_path_: PathBuf,
    pub reverse_path_: PathBuf,
    pub output_path_: PathBuf,
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
        _design_models: &Vec<Arc<dyn DesignModel>>,
        _decision_models: &Vec<Arc<dyn DecisionModel>>,
    ) -> IdentificationResult {
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
                return (identified, HashSet::new());
            }
        }
        (Vec::new(), HashSet::new())
    }

    fn reverse_identification(
        &self,
        _decision_model: &Vec<Arc<dyn DecisionModel>>,
        _design_model: &Vec<Arc<dyn DesignModel>>,
    ) -> Vec<Arc<dyn DesignModel>> {
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
                let reversed: Vec<Arc<dyn DesignModel>> = s
                    .lines()
                    .map(|p| {
                        let b = std::fs::read(p)
                            .expect("Failed to read header file from disk during identification");
                        let header = rmp_serde::from_slice::<DesignModelHeader>(b.as_slice())
                            .expect(
                            "Failed to deserialize header file from disk during identification.",
                        );
                        Arc::new(header) as Arc<dyn DesignModel>
                    })
                    .collect();
                return reversed;
            }
        }
        Vec::new()
    }
}
