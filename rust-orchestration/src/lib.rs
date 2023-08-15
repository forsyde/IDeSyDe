pub mod exploration;
pub mod identification;
pub mod models;

use std::borrow::BorrowMut;
use std::cmp::Ordering;

use std::collections::HashMap;
use std::io::BufRead;
use std::io::BufReader;
use std::io::BufWriter;
use std::io::Write;

use std::net::IpAddr;
use std::ops::DerefMut;
use std::path::Path;

use std::process::Child;
use std::process::ChildStdout;
use std::sync::Arc;
use std::sync::Mutex;

use exploration::ExternalExplorationModule;
use exploration::ExternalServerExplorationModule;
use identification::ExternalIdentificationModule;
use identification::ExternalServerIdentificationModule;

use idesyde_blueprints::DecisionModelMessage;
use idesyde_blueprints::DesignModelMessage;
use idesyde_core::DecisionModel;
use idesyde_core::DesignModel;
use idesyde_core::ExplorationModule;
use idesyde_core::IdentificationModule;

use rayon::prelude::*;
use reqwest::blocking::Response;
use reqwest::Error;

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
    fn get_client(&self) -> Arc<reqwest::blocking::Client>;

    fn get_address(&self) -> IpAddr;

    fn get_port(&self) -> usize;

    fn send_decision<T: DecisionModel + ?Sized>(&self, model: &T) -> Result<Response, Error> {
        let client = self.get_client();
        client
            .post(format!(
                "http://{}:{}/decision",
                self.get_address(),
                self.get_port()
            ))
            .body(DecisionModelMessage::from(model).to_json_str())
            .send()
    }

    fn send_design<T: DesignModel + ?Sized>(&self, model: &T) -> Result<Response, Error> {
        let client = self.get_client();
        client
            .post(format!(
                "http://{}:{}/design",
                self.get_address(),
                self.get_port()
            ))
            .body(DesignModelMessage::from(model).to_json_str())
            .send()
    }

    fn send_solved_decision<T: DecisionModel + ?Sized>(
        &self,
        model: &T,
    ) -> Result<Response, Error> {
        let client = self.get_client();
        client
            .post(format!(
                "http://{}:{}/solved",
                self.get_address(),
                self.get_port()
            ))
            .body(DecisionModelMessage::from(model).to_json_str())
            .send()
    }

    fn send_command(&self, command: &str, query: &Vec<(&str, &str)>) -> Result<Response, Error> {
        let client = self.get_client();
        client
            .get(format!(
                "http://{}:{}/{}",
                self.get_address(),
                self.get_port(),
                command
            ))
            .query(query)
            .send()
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
                        if let Some(imodule) =
                            ExternalServerIdentificationModule::try_create_local(prog.clone())
                        {
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
