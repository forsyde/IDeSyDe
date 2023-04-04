use std::{collections::HashSet, fs, path::Path};

use clap::Parser;
use idesyde_rust_core::IdentificationModule;
use sha3::Digest;

pub mod orchestration;

#[derive(Parser, Debug)]
#[command(
    name = "idesyde",
    author = "Rodolfo Jordao",
    about = "Orchestration and entry point for IDeSyDe."
)]
struct Args {
    // input files
    #[arg(help = "The input design models that IDeSyDe will identify and explore.")]
    inputs: Vec<String>,

    #[arg(
        short,
        long,
        help = "Sets set output file or directory.",
        long_help = "Sets set output file or directory. \n\
         If the output path is a file, IDeSyDe will write the latest solved/optimized design model in this file. \n\
         If the output path is a directory, IDeSyDe will write all solved/optimized design model in this directory."
    )]
    output_path: Option<String>,

    #[arg(
        long,
        default_value = "run",
        help = "Sets the running path that IDeSyDe uses."
    )]
    run_path: Option<String>,

    #[arg(
        long,
        help = "Sets the _total exploration_ time-out. \nIf non-positive, there is no time-out.",
        long_help = "Sets the _total exploration_ time-out. \nIf non-positive, there is no time-out. \nThe identification and integration stages are unnafected."
    )]
    x_total_time_out: Option<u64>,

    #[arg(
        long,
        help = "For explorer with mandatory discretization, this factor is used for the time discretization resolution.",
        group = "exploration"
    )]
    x_time_resolution: Option<u32>,
    #[arg(
        long,
        help = "For explorer with mandatory discretization, this factor is used for the memory discretization resolution.",
        group = "exploration"
    )]
    x_memory_resolution: Option<u32>,
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
        let run_path = Path::new(
            &args
                .run_path
                .expect("Failed to get run path durin initialization."),
        )
        .join(format!("{:x}", input_hash));

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
}
