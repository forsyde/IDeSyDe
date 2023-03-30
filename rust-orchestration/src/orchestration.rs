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

pub struct ExternalIdentificationModule {
    run_path_: PathBuf,
    imodule: std::process::Child,
}

impl PartialEq<ExternalIdentificationModule> for ExternalIdentificationModule {
    fn eq(&self, other: &ExternalIdentificationModule) -> bool {
        self.run_path() == other.run_path() && self.imodule.id() == other.imodule.id()
    }
}

impl Eq for ExternalIdentificationModule {}

impl Hash for ExternalIdentificationModule {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.run_path().hash(state);
        self.imodule.id().hash(state);
    }
}

impl IdentificationModule for ExternalIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.imodule.id().to_string()
    }

    fn run_path(&self) -> &Path {
        self.run_path_.as_path()
    }

    fn identification_step(&mut self, step_number: u64) -> HashSet<DecisionModelHeader> {
        let uid = self.unique_identifier();
        let run_path = self.run_path();
        let header_path = run_path.join("identified").join("msgpack");
        let known_decision_model_paths = load_decision_model_headers_from_binary(&header_path);
        let child = &mut self.imodule;
        if let Some(sin) = &child.stdin {
            let mut buf = BufWriter::new(sin);
            writeln!(&mut buf, "{}", step_number)
                .expect(format!("Error at writing for {}", uid).as_str());
        }
        // let mut new_decision_model_headers= HashSet::<DecisionModelHeader>::new();
        if let Some(stdout) = &mut child.stdout {
            let lines = BufReader::new(stdout).lines();
            lines
                .flat_map(|p| p.ok())
                .flat_map(|p| std::fs::read(p).ok())
                .flat_map(|b| rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()))
                .filter(|d| !known_decision_model_paths.contains(d))
                .collect::<HashSet<DecisionModelHeader>>()
        } else {
            HashSet::new()
        }
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

pub fn find_external_identification_modules(
    modules_path: &Path,
    run_path: &Path,
) -> HashSet<ExternalIdentificationModule> {
    if let Ok(read_dir) = modules_path.read_dir() {
        read_dir
            .flat_map(|e| e.ok())
            .map(|e| e.path())
            .filter(|p| p.is_file())
            .flat_map(|p| {
                let prog = p.read_link().unwrap_or(p);
                println!("{}", prog.to_str().unwrap());
                if let Ok(child) = std::process::Command::new(prog)
                    .arg("--integrate")
                    .arg(run_path)
                    .stdin(Stdio::piped())
                    .stdout(Stdio::piped())
                    .spawn()
                {
                    Some(ExternalIdentificationModule {
                        run_path_: run_path.to_owned(),
                        imodule: child,
                    })
                } else {
                    None
                }
            })
            .collect::<HashSet<ExternalIdentificationModule>>()
    } else {
        HashSet::new()
    }
}
