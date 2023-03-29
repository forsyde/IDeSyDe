use std::collections::HashSet;

use std::io::BufWriter;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

use idesyde_rust_core::DecisionModelHeader;
use idesyde_rust_core::IdentificationModule;

enum ExternalIdentificationModule {
    ExectuableFileIdentificationModule(String, Box<Path>, std::process::Child),
    PythonScriptIdentificationModule(String, Box<Path>, std::process::Child),
    JVMJarIdentificationModule(String, Box<Path>, std::process::Child),
}

impl IdentificationModule for ExternalIdentificationModule {
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

    fn identify(&self, iteration: u64) -> HashSet<DecisionModelHeader> {
        let child = match self {
            Self::ExectuableFileIdentificationModule(_, _, c) => c,
            Self::PythonScriptIdentificationModule(_, _, c) => c,
            Self::JVMJarIdentificationModule(_, _, c) => c,
        };
        let header_path = self.run_path().join("identified").join("msgpack");
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
        if let Some(sin) = &child.stdin {
            let mut buf = BufWriter::new(sin);
            writeln!(&mut buf, "{}", iteration)
                .expect(format!("Error at writing for {}", self.unique_identifier()).as_str());
        }
        HashSet::new()
    }
}
