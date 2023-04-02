use std::{collections::HashSet, fs, path::Path};

use clap::Parser;
use idesyde_rust_core::IdentificationModule;
use sha3::Digest;

pub mod orchestration;

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
struct Args {
    /// input files
    inputs: Vec<String>,
}

fn main() {
    let args = Args::parse();
    if args.inputs.len() > 0 {
        let mut hasher = sha3::Sha3_224::new();
        let mut sorted_inputs = args.inputs.clone();
        sorted_inputs.sort();
        for input in &sorted_inputs {
            if let Ok(f) = fs::read(input) {
                hasher.update(f.as_slice());
            }
        }
        let input_hash = hasher.finalize();
        let run_path = Path::new("run").join(format!("{:x}", input_hash));
        std::fs::create_dir_all(run_path.join("inputs").join("fiodl")).unwrap();
        std::fs::create_dir_all(run_path.join("inputs").join("json")).unwrap();
        std::fs::create_dir_all(run_path.join("inputs").join("msgpack")).unwrap();
        std::fs::create_dir_all(run_path.join("identified").join("json")).unwrap();
        std::fs::create_dir_all(run_path.join("identified").join("msgpack")).unwrap();
        for input in &sorted_inputs {
            let p = Path::new(input);
            match p.extension().and_then(|s| s.to_str()) {
                Some("fiodl") => {
                    if let Some(fname) = p.file_name() {
                        let fpath = Path::new(fname);
                        fs::copy(p, run_path.join("inputs").join("fiodl").join(fpath)).unwrap();
                    }
                }
                Some(_) | None => break,
            };
        }
        let imodules =
            orchestration::find_identification_modules(Path::new("imodules"), run_path.as_path());
        for imodule in &imodules {
            imodule.identification_step(0);
        }
        for imodule in &imodules {
            imodule.identification_step(1);
        }
    } else {
        println!("At least one input design model is necessary")
    }
    println!("done");
}
