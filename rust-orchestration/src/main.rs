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

        std::fs::create_dir_all(run_path.join("inputs").join("fiodl"))
            .expect("Failed to create input directory during identification.");
        std::fs::create_dir_all(run_path.join("inputs").join("json"))
            .expect("Failed to create input directory during identification.");
        std::fs::create_dir_all(run_path.join("inputs").join("msgpack"))
            .expect("Failed to create input directory during identification.");
        for input in &sorted_inputs {
            let p = Path::new(input);
            match p.extension().and_then(|s| s.to_str()) {
                Some("fiodl") => {
                    if let Some(fname) = p.file_name() {
                        let fpath = Path::new(fname);
                        fs::copy(p, run_path.join("inputs").join("fiodl").join(fpath))
                            .expect("Failed to copy input models during identification.");
                    }
                }
                Some(_) | None => break,
            };
        }

        let mut imodules = HashSet::<Box<dyn IdentificationModule>>::new();
        for eximod in
            orchestration::find_identification_modules(Path::new("imodules"), run_path.as_path())
        {
            imodules.insert(Box::new(eximod));
        }

        let found = orchestration::identification_procedure(run_path.as_path(), &imodules);

        let dominant_msgpacks = run_path.join("dominant").join("msgpack");
        let dominant_jsons = run_path.join("dominant").join("json");
        std::fs::create_dir_all(&dominant_jsons)
            .expect("Failed to create dominant directory during identification.");
        std::fs::create_dir_all(&dominant_msgpacks)
            .expect("Failed to create dominant directory during identification.");
        let dominant = orchestration::compute_dominant_decision_models(&found);

        let mut iter = 0;
        for m in &dominant {
            let pj = &dominant_jsons.join(format!("header_{}_{}.json", iter, m.category));
            let pm = &dominant_msgpacks.join(format!("header_{}_{}.msgpack", iter, m.category));
            fs::write(
                pj,
                serde_json::to_string(m)
                    .expect("Failed to serialize dominant model during identification."),
            );
            fs::write(
                pm,
                rmp_serde::to_vec(m)
                    .expect("Failed to serialize dominant model during identification."),
            );
            iter += 1;
        }

        println!("found {:?}", dominant)
    } else {
        println!("At least one input design model is necessary")
    }
    println!("done");
}
