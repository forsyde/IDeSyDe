use std::collections::HashSet;

use std::hash::Hash;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;

use idesyde_rust_core::DecisionModelHeader;
use idesyde_rust_core::IdentificationModule;

pub struct ExternalIdentificationModule {
    run_path_: PathBuf,
    command_path_: PathBuf,
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
        let mut headers = HashSet::new();
        let run_path = self.run_path();
        let header_path = run_path.join("identified").join("msgpack");
        let known_decision_model_paths = load_decision_model_headers_from_binary(&header_path);
        let is_java = self
            .command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let output = match is_java {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(self.command_path_.as_os_str())
                .arg("--no-integration")
                .arg(self.run_path_.as_os_str())
                .arg(step_number.to_string())
                .stdout(Stdio::piped())
                .output(),
            false => std::process::Command::new(self.command_path_.as_os_str())
                .arg("--no-integration")
                .arg(self.run_path_.as_os_str())
                .arg(step_number.to_string())
                .stdout(Stdio::piped())
                .output(),
        };
        if let Ok(out) = output {
            if let Ok(s) = String::from_utf8(out.stdout) {
                for p in s.lines() {
                    if let Ok(b) = std::fs::read(p) {
                        if let Ok(header) =
                            rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice())
                        {
                            if !known_decision_model_paths.contains(&header) {
                                headers.insert(header);
                            }
                        }
                    }
                }
            }
        }
        headers
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

pub fn find_identification_modules(
    modules_path: &Path,
    run_path: &Path,
) -> HashSet<ExternalIdentificationModule> {
    let mut imodules = HashSet::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    imodules.insert(ExternalIdentificationModule {
                        run_path_: run_path.to_path_buf().clone(),
                        command_path_: prog.clone(),
                    });
                }
            }
        }
    }
    imodules
}
