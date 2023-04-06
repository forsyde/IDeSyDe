use std::cmp::Ordering;
use std::collections::HashMap;

use std::hash::Hash;
use std::io::BufRead;
use std::io::BufReader;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;

use idesyde_rust_core::DecisionModel;
use idesyde_rust_core::DecisionModelHeader;
use idesyde_rust_core::DesignModel;
use idesyde_rust_core::DesignModelHeader;
use idesyde_rust_core::ExplorationCombinationDescription;
use idesyde_rust_core::ExplorationModule;
use idesyde_rust_core::IdentificationModule;

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalIdentificationModule {
    command_path_: PathBuf,
}

impl IdentificationModule for ExternalIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn identification_step(
        &self,
        iteration: i32,
        decision_path: &Path,
        design_path: &Path,
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
                .arg("--no-integration")
                .arg(design_path)
                .arg(decision_path)
                .arg(iteration.to_string())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            false => std::process::Command::new(&self.command_path_)
                .arg("--no-integration")
                .arg(design_path)
                .arg(decision_path)
                .arg(iteration.to_string())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
        };
        if let Ok(out) = output {
            if let Ok(s) = String::from_utf8(out.stdout) {
                let identified: Vec<Box<dyn DecisionModel>> = s
                    .lines()
                    .map(|p| {
                        let b = std::fs::read(p)
                            .expect("Failed to read header file from disk during identification");
                        let header = rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice())
                            .expect(
                            "Failed to deserialize header file from disk during identification.",
                        );
                        Box::new(header) as Box<dyn DecisionModel>
                    })
                    .collect();
                return identified;
            }
        }
        Vec::new()
    }
}

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalExplorationModule {
    command_path_: PathBuf,
}

impl ExplorationModule for ExternalExplorationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn available_criterias(
        &self,
        decision_path: &Path,
        solution_path: &Path,
        m: Box<dyn idesyde_rust_core::DecisionModel>,
    ) -> std::collections::HashMap<String, f32> {
        HashMap::new() // TODO: put interfaces later
    }

    fn get_combination(
        &self,
        decision_path: &Path,
        solution_path: &Path,
        m: &Box<dyn idesyde_rust_core::DecisionModel>,
    ) -> idesyde_rust_core::ExplorationCombinationDescription {
        let headers = load_decision_model_headers_from_binary(decision_path);
        let chosen_path = headers
            .iter()
            .find(|(p, h)| h == &m.header())
            .map(|(p, h)| p)
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
                .arg(decision_path)
                .arg(solution_path)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            false => std::process::Command::new(&self.command_path_)
                .arg("-c")
                .arg(chosen_path)
                .arg(decision_path)
                .arg(solution_path)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
        };
        let o = output
            .expect("Failed to get combination from exploration module.")
            .stdout;
        serde_json::from_slice(o.as_slice())
            .expect("Failed to deserialize combination from exploration module.")
    }

    fn explore(
        &self,
        decision_path: &Path,
        solution_path: &Path,
        m: &Box<dyn idesyde_rust_core::DecisionModel>,
    ) -> Box<dyn Iterator<Item = Box<dyn DecisionModel>>> {
        let headers = load_decision_model_headers_from_binary(decision_path);
        let chosen_path = headers
            .iter()
            .find(|(p, h)| h == &m.header())
            .map(|(p, h)| p)
            .unwrap();
        let is_java = self
            .command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let child = match is_java {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(&self.command_path_)
                .arg("-e")
                .arg(chosen_path)
                .arg(decision_path)
                .arg(solution_path)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
            false => std::process::Command::new(&self.command_path_)
                .arg("-e")
                .arg(chosen_path)
                .arg(decision_path)
                .arg(solution_path)
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .spawn(),
        };
        let out = child
            .expect("Failed to initiate explorer")
            .stdout
            .expect("Failed to acquire explorer STDOUT");
        let buf = BufReader::new(out);
        Box::new(
            buf.lines()
                .map(|l| l.expect("Failed to read solution during exploration"))
                .map(|f| std::fs::read(f).expect("Failed to read solution file during exploration"))
                .map(|b| {
                    rmp_serde::from_slice::<DecisionModelHeader>(&b)
                        .expect("Failed to deserialize solution header during exploration")
                })
                .map(|m| Box::new(m) as Box<dyn DecisionModel>),
        )
        // Box::new(BufRead::lines(out))
    }
}

pub fn load_decision_model_headers_from_binary(
    header_path: &Path,
) -> Vec<(PathBuf, DecisionModelHeader)> {
    let known_decision_model_paths = if let Ok(ls) = header_path.read_dir() {
        ls.flat_map(|dir_entry_r| {
            if let Ok(dir_entry) = dir_entry_r {
                if dir_entry
                    .path()
                    .file_name()
                    .and_then(|f| f.to_str())
                    .map_or(false, |f| f.starts_with("header"))
                    && dir_entry
                        .path()
                        .extension()
                        .map_or(false, |ext| ext.eq_ignore_ascii_case("msgpack"))
                {
                    return Some(dir_entry.path());
                }
            }
            None
        })
        .collect::<Vec<PathBuf>>()
    } else {
        Vec::new()
    };
    known_decision_model_paths
        .iter()
        .map(|p| {
            (
                p,
                std::fs::read(p).expect("Failed to read decision model header file."),
            )
        })
        .map(|(p, b)| {
            (
                p.to_owned(),
                rmp_serde::decode::from_slice(&b)
                    .expect("Failed to deserialize deicsion model header."),
            )
        })
        .collect()
}

pub fn load_design_model_headers_from_binary(header_path: &Path) -> Vec<DesignModelHeader> {
    let known_design_model_paths = if let Ok(ls) = header_path.read_dir() {
        ls.flat_map(|dir_entry_r| {
            if let Ok(dir_entry) = dir_entry_r {
                if dir_entry
                    .path()
                    .file_name()
                    .and_then(|f| f.to_str())
                    .map_or(false, |f| f.starts_with("header"))
                    && dir_entry
                        .path()
                        .extension()
                        .map_or(false, |ext| ext.eq_ignore_ascii_case("msgpack"))
                {
                    return Some(dir_entry.path());
                }
            }
            None
        })
        .collect::<Vec<PathBuf>>()
    } else {
        Vec::new()
    };
    known_design_model_paths
        .iter()
        .flat_map(|p| std::fs::read(p))
        .flat_map(|b| rmp_serde::decode::from_slice(&b).ok())
        .collect()
}

pub fn find_identification_modules(modules_path: &Path) -> Vec<ExternalIdentificationModule> {
    let mut imodules = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    let is_java = prog
                        .extension()
                        .and_then(|s| s.to_str())
                        .map(|s| s == "jar")
                        .unwrap_or(false);
                    if is_java {
                        imodules.push(ExternalIdentificationModule {
                            command_path_: prog.clone(),
                        });
                    } else {
                        imodules.push(ExternalIdentificationModule {
                            command_path_: prog.clone(),
                        });
                    }
                }
            }
        }
    }
    imodules
}

pub fn find_exploration_modules(modules_path: &Path) -> Vec<ExternalExplorationModule> {
    let mut emodules = Vec::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    let is_java = prog
                        .extension()
                        .and_then(|s| s.to_str())
                        .map(|s| s == "jar")
                        .unwrap_or(false);
                    if is_java {
                        emodules.push(ExternalExplorationModule {
                            command_path_: prog.clone(),
                        });
                    } else {
                        emodules.push(ExternalExplorationModule {
                            command_path_: prog.clone(),
                        });
                    }
                }
            }
        }
    }
    emodules
}

pub fn identification_procedure(
    run_path: &Path,
    imodules: &Vec<&dyn IdentificationModule>,
) -> Vec<Box<dyn DecisionModel>> {
    let decision_path = &run_path.join("identified");
    let design_path = &run_path.join("inputs");
    std::fs::create_dir_all(decision_path)
        .expect("Failed to created 'identified' folder during identification.");
    std::fs::create_dir_all(design_path)
        .expect("Failed to created 'inputs' folder during identification.");
    let mut identified: Vec<Box<dyn DecisionModel>> =
        load_decision_model_headers_from_binary(&decision_path)
            .iter()
            .map(|(p, h)| Box::new(h.to_owned()) as Box<dyn DecisionModel>)
            .collect();
    let design_model_headers = load_design_model_headers_from_binary(&design_path);
    let design_models: Vec<Box<dyn DesignModel>> = design_model_headers
        .iter()
        .map(|h| Box::new(h.to_owned()) as Box<dyn DesignModel>)
        .collect();
    let mut step = identified.len() as i32;
    let mut fix_point = false;
    while !fix_point {
        fix_point = true;
        for &imodule in imodules {
            let mut potential = imodule.identification_step(
                step,
                &decision_path,
                &design_path,
                &design_models,
                &identified,
            );
            potential.retain(|m| !identified.contains(m));
            if potential.len() > 0 {
                fix_point = fix_point && false;
            }
            for m in potential {
                // if let Some(b_path_str) = &m.header().body_path.first() {
                //     let b_path = Path::new(b_path_str);
                //     std::fs::remove_file(b_path)
                //         .expect("Failed to remove redundant decision model body");
                // };
                identified.push(m);
            }
        }
        step += 1;
    }
    identified
}

pub fn compute_dominant_combinations<'a>(
    decision_path: &Path,
    solution_path: &Path,
    decision_models: &'a Vec<Box<dyn DecisionModel>>,
    exploration_modules: &'a Vec<Box<dyn ExplorationModule>>,
) -> Vec<(&'a Box<dyn ExplorationModule>, &'a Box<dyn DecisionModel>)> {
    let combinations: Vec<(
        &Box<dyn ExplorationModule>,
        &Box<dyn DecisionModel>,
        ExplorationCombinationDescription,
    )> = exploration_modules
        .into_iter()
        .flat_map(|exp| {
            decision_models
                .iter()
                .map(move |m| (exp, m, exp.get_combination(decision_path, solution_path, m)))
        })
        .filter(|(_, _, c)| c.can_explore)
        .collect();
    combinations
        .iter()
        .filter(|(_, m, _)| {
            combinations
                .iter()
                .all(|(_, o, _)| match m.partial_cmp(&o) {
                    Some(Ordering::Greater) | Some(Ordering::Equal) | None => true,
                    _ => false,
                })
        })
        .filter(|(_, _, comb)| {
            combinations
                .iter()
                .all(|(_, _, ocomb)| match comb.partial_cmp(&ocomb) {
                    Some(Ordering::Greater) | Some(Ordering::Equal) | None => true,
                    _ => false,
                })
        })
        .map(|(e, m, _)| (*e, *m))
        .collect()
}
