use std::collections::HashSet;

use std::hash::Hash;
use std::io::BufRead;
use std::io::BufReader;
use std::io::BufWriter;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;

use idesyde_rust_core::DecisionModelHeader;
use idesyde_rust_core::IdentificationModule;

enum ExternalIdentificationModule<'a> {
    ExectuableFileIdentificationModule(&'a str, &'a PathBuf, std::process::Child),
    PythonScriptIdentificationModule(&'a str, &'a PathBuf, std::process::Child),
    JVMJarIdentificationModule(&'a str, &'a PathBuf, std::process::Child),
}

impl<'a> PartialEq<ExternalIdentificationModule<'a>> for ExternalIdentificationModule<'a> {

    fn eq(&self, other: &ExternalIdentificationModule<'a>) -> bool {
        match (self, other) {
            (Self::ExectuableFileIdentificationModule(l0, l1, l2), Self::ExectuableFileIdentificationModule(r0, r1, r2)) => l0 == r0 && l1 == r1,
            (Self::PythonScriptIdentificationModule(l0, l1, l2), Self::PythonScriptIdentificationModule(r0, r1, r2)) => l0 == r0 && l1 == r1,
            (Self::JVMJarIdentificationModule(l0, l1, l2), Self::JVMJarIdentificationModule(r0, r1, r2)) => l0 == r0 && l1 == r1,
            _ => false,
        }
    }
}

impl<'a> Eq for ExternalIdentificationModule<'a> {}

impl<'a> Hash for ExternalIdentificationModule<'a> {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        match self {
            Self::ExectuableFileIdentificationModule(l0, l1, l2) => {
                l0.hash(state);
                l1.hash(state);
            },
            Self::PythonScriptIdentificationModule(l0, l1, l2) => {
                l0.hash(state);
                l1.hash(state);
            },
            Self::JVMJarIdentificationModule(l0, l1, l2) => {
                l0.hash(state);
                l1.hash(state);
            },
        }
    }
}

impl<'a> IdentificationModule for ExternalIdentificationModule<'a> {
    fn unique_identifier(&self) -> &str {
        match self {
            Self::ExectuableFileIdentificationModule(p, _, _) => &p[..],
            Self::PythonScriptIdentificationModule(p, _, _) => &p[..],
            Self::JVMJarIdentificationModule(p, _, _) => &p[..],
        }
    }

    fn run_path(&self) -> &Path {
        match self {
            Self::ExectuableFileIdentificationModule(_, p, _) => p,
            Self::PythonScriptIdentificationModule(_, p, _) => p,
            Self::JVMJarIdentificationModule(_, p, _) => p,
        }
    }

    fn identification_step(&mut self, step_number: u64) -> HashSet<DecisionModelHeader> {
        let (uid, run_path, child) = match self {
            Self::ExectuableFileIdentificationModule(i, p, c) => (i, p, c),
            Self::PythonScriptIdentificationModule(i, p, c) => (i, p, c),
            Self::JVMJarIdentificationModule(i, p, c) => (i, p, c),
        };
        let header_path = run_path.join("identified").join("msgpack");
        let known_decision_model_paths = load_decision_model_headers_from_binary(&header_path);
        if let Some(sin) = &child.stdin {
            let mut buf = BufWriter::new(sin);
            writeln!(&mut buf, "{}", step_number)
                .expect(format!("Error at writing for {}", uid).as_str());
        }
        // let mut new_decision_model_headers= HashSet::<DecisionModelHeader>::new();
        if let Some(sout) = &mut child.stdout {
            let lines = BufReader::new(sout).lines();
            lines
                .flat_map(|p| p.ok())
                .flat_map(|p| std::fs::read(p).ok())
                .flat_map(|b| rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()))
                .filter(|d| !known_decision_model_paths.contains(d))
                .collect::<HashSet<DecisionModelHeader>>()
        } else { HashSet::new() }
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

pub fn find_external_identification_modules<'a>(modules_path: &'a PathBuf, run_path: &'a PathBuf) -> HashSet<ExternalIdentificationModule<'a>> {
    if let Ok(readDir) = modules_path.read_dir() {
        readDir
            .flat_map(|e| e.ok())
            .map(|e| e.path())
            .filter(|p| p.is_file())
            .flat_map(|p| {
                match p.extension().and_then(|s| s.to_str()) {
                    Some("jar") => {
                        if let Ok(mut child) = std::process::Command::new("java").arg("-jar").arg(p).arg("--integrate").arg(run_path).stdin(Stdio::piped()).stdout(Stdio::piped()).spawn() {
                          Some(ExternalIdentificationModule::JVMJarIdentificationModule(p.to_str().expect("Could not get module's UID"), run_path, child))
                        } else { None }
                    }
                    Some("py") => { None }
                    _ => { None }
                }
            })
            .collect::<HashSet<ExternalIdentificationModule<'a>>>()
    } else { HashSet::new() }
}