use std::cmp::Ordering;
use std::collections::HashMap;

use std::hash::Hash;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Write;

use std::path::Path;
use std::path::PathBuf;
use std::process::Child;

use std::process::ChildStdin;
use std::process::Stdio;

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

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalIdentificationModule {
    command_path_: PathBuf,
    inputs_path_: PathBuf,
    identified_path_: PathBuf,
    solved_path_: PathBuf,
    reverse_path_: PathBuf,
    output_path_: PathBuf,
}

impl IdentificationModule for ExternalIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn identification_step(
        &self,
        iteration: i32,
        _design_models: &Vec<Box<dyn DesignModel>>,
        _decision_models: &Vec<Box<dyn DecisionModel>>,
    ) -> Vec<Box<dyn DecisionModel>> {
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
                let identified: Vec<Box<dyn DecisionModel>> = s
                    .lines()
                    .flat_map(|p| {
                        if let Ok(b) = std::fs::read(p) {
                            if let Ok(header) = rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()) {
                                return Some(Box::new(header) as Box<dyn DecisionModel>);
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
        _decision_model: &Vec<Box<dyn DecisionModel>>,
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

    fn bid(&self, m: &Box<dyn idesyde_core::DecisionModel>) -> Vec<ExplorationBid> {
        let headers = load_decision_model_headers_from_binary(&self.identified_path_);
        let chosen_path = headers
            .iter()
            .find(|(_, h)| h == &m.header())
            .map(|(p, _)| p)
            .unwrap();
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
        m: &Box<dyn idesyde_core::DecisionModel>,
        explorer_idx: usize,
        max_sols: i64,
        total_timeout: i64,
        time_resolution: i64,
        memory_resolution: i64,
    ) -> Box<dyn Iterator<Item = Box<dyn DecisionModel>>> {
        let headers = load_decision_model_headers_from_binary(&self.identified_path_);
        let chosen_path = headers
            .iter()
            .find(|(_, h)| h == &m.header())
            .map(|(p, _)| p)
            .unwrap();
        let is_java = self
            .command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let child_opt = match is_java {
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
                .arg(explorer_idx.to_string())
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
                .arg(explorer_idx.to_string())
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
                    // if (f.ends_with(".msgpack")) {
                    //     match std::fs::read(&f).and_then(|b| rmp_serde::from_slice::<DecisionModelHeader>(&b)) {
                    //         Ok(h) => Some(h),
                    //         _ => None
                    //     }
                    // } else if (f.ends_with(".cbor")) {
                    //     match std::fs::read(&f).and_then(|b| ::from_slice::<DecisionModelHeader>(&b)) {
                    //         Ok(h) => Some(h),
                    //         _ => None
                    //     }
                    // } else {
                    //     None
                    // }
                })
                .map(|m| Box::new(m) as Box<dyn DecisionModel>),
        )
        // Box::new(BufRead::lines(out))
    }
}

pub fn find_and_prepare_identification_modules(
    modules_path: &Path,
    identified_path: &Path,
    inputs_path: &Path,
    solved_path: &Path,
    integration_path: &Path,
    output_path: &Path,
) -> Vec<ExternalIdentificationModule> {
    let mut imodules = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    imodules.push(ExternalIdentificationModule {
                        command_path_: prog.clone(),
                        identified_path_: identified_path.to_path_buf(),
                        inputs_path_: inputs_path.to_path_buf(),
                        solved_path_: solved_path.to_path_buf(),
                        reverse_path_: integration_path.to_path_buf(),
                        output_path_: output_path.to_path_buf(),
                    });
                }
            }
        }
    }
    imodules
}

pub fn find_exploration_modules(
    modules_path: &Path,
    identified_path: &Path,
    solved_path: &Path,
) -> Vec<ExternalExplorationModule> {
    let mut emodules = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    emodules.push(ExternalExplorationModule {
                        command_path_: prog.clone(),
                        identified_path_: identified_path.to_path_buf(),
                        solved_path_: solved_path.to_path_buf(),
                    });
                }
            }
        }
    }
    emodules
}

pub fn identification_procedure(
    imodules: &Vec<Box<dyn IdentificationModule>>,
    design_models: &Vec<Box<dyn DesignModel>>,
    pre_identified: &mut Vec<Box<dyn DecisionModel>>,
    starting_iter: i32,
) -> Vec<Box<dyn DecisionModel>> {
    let mut step = starting_iter;
    let mut fix_point = false;
    let mut identified: Vec<Box<dyn DecisionModel>> = Vec::new();
    identified.append(pre_identified);
    while !fix_point || step <= 1 {
        // the step condition forces the procedure to go at least one more, fundamental for incrementability
        fix_point = true;
        let before = identified.len();
        let new_identified: Vec<Box<dyn DecisionModel>> = imodules
            .par_iter()
            .flat_map(|imodule| imodule.identification_step(step, &design_models, &identified))
            .filter(|potential| !identified.contains(potential))
            .collect();
        identified.extend(new_identified.into_iter());
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
    decision_models: &'a Vec<&'a Box<dyn DecisionModel>>,
) -> Vec<&'a Box<dyn DecisionModel>> {
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
    command_path_: PathBuf,
    inputs_path_: PathBuf,
    identified_path_: PathBuf,
    solved_path_: PathBuf,
    reverse_path_: PathBuf,
    output_path_: PathBuf,
    process: Child,
}

impl PartialEq for ExternalServerIdentificationModule {
    fn eq(&self, other: &Self) -> bool {
        self.command_path_ == other.command_path_
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
        self.command_path_.hash(state);
    }
}

impl Drop for ExternalServerIdentificationModule {
    fn drop(&mut self) {
        if let Some(input) = &mut self.process.stdin {
            writeln!(input, "EXIT").expect("Could not send EXIT to a identification module");
        };
    }
}

impl IdentificationModule for ExternalServerIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn identification_step(
        &self,
        iteration: i32,
        design_models: &Vec<Box<dyn DesignModel>>,
        decision_models: &Vec<Box<dyn DecisionModel>>,
    ) -> Vec<Box<dyn DecisionModel>> {
        let identified = Vec::new();
        // save decision models and design models and ask the module to read them
        for design_model in design_models {
            if design_model.header().write_to_dir(
                self.inputs_path_.as_path(),
                format!("{:0>16}", iteration).as_str(),
                self.unique_identifier().as_str(),
            ) {
                if let Some(childin) = &self.process.stdin {
                    writeln!(
                        childin.clone().by_ref(),
                        "DESIGN {}_{}_{}.msgpack",
                        self.inputs_path_
                            .to_str()
                            .expect("Failed to cast path to str during external identification"),
                        format!("{:0>16}", iteration).as_str(),
                        self.unique_identifier().as_str()
                    )
                    .expect("Failed to request identification module to read decision reader");
                }
            }
        }
        for decision_model in decision_models {
            decision_model.write_to_dir(
                self.inputs_path_.as_path(),
                format!("{:0>16}", iteration).as_str(),
                self.unique_identifier().as_str(),
            );
            if let Some(childin) = &self.process.stdin {
                writeln!(
                    childin.clone().by_ref(),
                    "DECISION HEADER {}_{}_{}.msgpack",
                    self.inputs_path_
                        .to_str()
                        .expect("Failed to cast path to str during external identification"),
                    format!("{:0>16}", iteration).as_str(),
                    self.unique_identifier().as_str()
                )
                .expect("Failed to request identification module to read decision reader");
            }
        }
        // ask the module to perform identification
        if let Some(childin) = &self.process.stdin {
            writeln!(childin.clone().by_ref(), "IDENTIFY {}", iteration)
                .expect("Failed to request identification module to identify");
        }
        // TODO: Continue here
        // .. now get the decision models
        identified
    }

    fn reverse_identification(
        &self,
        decision_model: &Vec<Box<dyn DecisionModel>>,
        design_model: &Vec<Box<dyn DesignModel>>,
    ) -> Vec<Box<dyn DesignModel>> {
        todo!()
    }
}
