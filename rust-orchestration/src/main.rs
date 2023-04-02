use std::{fs, path::Path};

use clap::Parser;
use idesyde_rust_core::IdentificationModule;
use sha3::{Digest, Sha3_256};

pub mod orchestration;

#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
struct Args {
    /// input files
    #[arg(long)]
    inputs: Vec<String>,
}

fn main() {
    let args = Args::parse();
    let mut hasher = sha3::Sha3_256::new();
    let mut sorted_inputs = args.inputs.clone();
    sorted_inputs.sort();
    for input in sorted_inputs {
        if let Ok(f) = fs::read(input) {
            hasher.update(f.as_slice());
        }
    }
    let input_hash = hasher.finalize();
    let run_path = Path::new("run")
        .join(String::from_utf8(input_hash.to_vec()).unwrap_or("default".to_string()));
    let imodules =
        orchestration::find_identification_modules(Path::new("imodules"), Path::new("run"));
    std::fs::create_dir_all(run_path.join("inputs").join("fiodl")).unwrap();
    std::fs::create_dir_all(run_path.join("inputs").join("json")).unwrap();
    std::fs::create_dir_all(run_path.join("inputs").join("msgpack")).unwrap();
    std::fs::create_dir_all(run_path.join("identified").join("json")).unwrap();
    std::fs::create_dir_all(run_path.join("identified").join("msgpack")).unwrap();
    for imodule in &imodules {
        imodule.identification_step(0);
    }
    std::thread::sleep(std::time::Duration::from_secs(1));
    for imodule in &imodules {
        imodule.identification_step(-1);
    }
    println!("done");
}
