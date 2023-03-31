use core::time;
use std::collections::HashSet;

use std::hash::Hash;
use std::io::BufRead;
use std::io::BufReader;
use std::io::BufWriter;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;
use std::str::FromStr;

use idesyde_rust_core::DecisionModelHeader;
use idesyde_rust_core::IdentificationModule;

pub struct ExternalIdentificationModule {
    run_path_: PathBuf,
    command_path_: PathBuf,
    step_ch_in: std::sync::mpsc::Sender<i32>,
    nidentified_ch_out: std::sync::mpsc::Receiver<u32>,
    headers_paths_ch_out: std::sync::mpsc::Receiver<String>,
}

impl PartialEq<ExternalIdentificationModule> for ExternalIdentificationModule {
    fn eq(&self, other: &ExternalIdentificationModule) -> bool {
        self.run_path() == other.run_path() && self.command_path_ == other.command_path_
    }
}

impl Eq for ExternalIdentificationModule {}

impl Hash for ExternalIdentificationModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.run_path_.hash(state);
        self.command_path_.hash(state);
    }
}

impl IdentificationModule for ExternalIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn run_path(&self) -> &Path {
        self.run_path_.as_path()
    }

    fn identification_step(&self, step_number: i32) -> HashSet<DecisionModelHeader> {
        let run_path = self.run_path();
        let header_path = run_path.join("identified").join("msgpack");
        let known_decision_model_paths = load_decision_model_headers_from_binary(&header_path);
        if let Ok(_) = self.step_ch_in.send(step_number) {
            if let Ok(n) = self.nidentified_ch_out.recv() {
                return (0..n)
                    .flat_map(|i| self.headers_paths_ch_out.recv())
                    .flat_map(|p| std::fs::read(p).ok())
                    .flat_map(|b| rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()))
                    .filter(|d| !known_decision_model_paths.contains(d))
                    .collect::<HashSet<DecisionModelHeader>>();
            }
        }
        HashSet::new()
    }
}

pub fn load_decision_model_headers_from_binary(header_path: &Path) -> HashSet<DecisionModelHeader> {
    let known_decision_model_paths = if let Ok(ls) = header_path.read_dir() {
        ls.flat_map(|dir_entry_r| {
            if let Ok(dir_entry) = dir_entry_r {
                if dir_entry.path().starts_with("header")
                    && dir_entry
                        .path()
                        .extension()
                        .map_or(false, |ext| ext == "msgpack")
                {
                    return Some(dir_entry.path());
                }
            }
            None
        })
        .collect::<HashSet<PathBuf>>()
    } else {
        HashSet::new()
    };
    known_decision_model_paths
        .iter()
        .flat_map(|p| std::fs::read(p))
        .flat_map(|b| rmp_serde::decode::from_slice(&b).ok())
        .collect::<HashSet<DecisionModelHeader>>()
}

pub fn initiate_identification_modules(
    modules_path: &Path,
    run_path: &Path,
) -> HashSet<ExternalIdentificationModule> {
    let mut imodules = HashSet::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if (p.is_file()) {
                    let prog = p.read_link().unwrap_or(p);
                    let (num_tx, num_rx) = std::sync::mpsc::channel();
                    let (nidentified_tx, nidentified_rx) = std::sync::mpsc::channel();
                    let (headers_tx, headers_rx) = std::sync::mpsc::channel();
                    imodules.insert(ExternalIdentificationModule {
                        run_path_: run_path.to_owned(),
                        command_path_: prog.clone(),
                        step_ch_in: num_tx,
                        nidentified_ch_out: nidentified_rx,
                        headers_paths_ch_out: headers_rx,
                    });
                    let mut command = match prog.extension().and_then(|s| s.to_str()) {
                        Some("jar") => std::process::Command::new(format!(
                            "java -jar {} --no-integration {} ",
                            prog.display(),
                            run_path.display()
                        )),
                        Some(_) | None => std::process::Command::new(format!(
                            "{} --no-integration {} ",
                            prog.display(),
                            run_path.display()
                        )),
                    };
                    std::thread::spawn(move || loop {
                        match num_rx.recv() {
                            Ok(i) => {
                                let mut child = command
                                    .arg(i.to_string())
                                    .stdin(Stdio::piped())
                                    .stdout(Stdio::piped())
                                    .spawn()
                                    .unwrap();
                                if i >= 0 {
                                    child.wait();
                                    if let Some(stdout) = &mut child.stdout {
                                        let mut count = 0;
                                        for s in BufReader::new(stdout).lines().flat_map(|l| l.ok())
                                        {
                                            headers_tx.send(s);
                                            count += 1;
                                        }
                                        nidentified_tx.send(count);
                                    }
                                } else {
                                    break;
                                }
                            }
                            Err(_) => break,
                        }
                    });
                }
            }
        }
    }
    imodules
}
