use std::{fs, path::Path};

use clap::Parser;
use idesyde_rust_core::{DecisionModel, ExplorationModule, IdentificationModule};
use sha3::Digest;

use crate::orchestration::compute_dominant_combinations;

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
        help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit",
        long_help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit. \nThe identification and integration stages are unnafected.",
        group = "exploration"
    )]
    x_max_solutions: Option<u32>,

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
        let run_path_str = &args
            .run_path
            .expect("Failed to get run path durin initialization.");
        let run_path = Path::new(run_path_str);
        let inputs_path = &run_path.join("inputs");
        let imodules_path = &std::env::current_dir()
            .expect("Failed to get working directory.")
            .join("imodules");
        let emodules_path = &std::env::current_dir()
            .expect("Failed to get working directory.")
            .join("emodules");
        let identified_path = run_path.join("identified");
        let solution_path = &run_path.join("explored");

        std::fs::create_dir_all(inputs_path)
            .expect("Failed to create input directory during identification.");
        std::fs::create_dir_all(imodules_path)
            .expect("Failed to create imodules directory during identification.");
        std::fs::create_dir_all(emodules_path)
            .expect("Failed to create emodules directory during identification.");
        std::fs::create_dir_all(&identified_path)
            .expect("Failed to create identified directory during identification.");
        std::fs::create_dir_all(&solution_path)
            .expect("Failed to create explored directory during identification.");

        for input in &sorted_inputs {
            let p = Path::new(input);
            match p.extension().and_then(|s| s.to_str()) {
                Some("fiodl") => {
                    if let Some(fname) = p.file_name() {
                        let fpath = Path::new(fname);
                        fs::copy(p, run_path.join("inputs").join(fpath))
                            .expect("Failed to copy input models during identification.");
                    }
                }
                Some(_) | None => break,
            };
        }

        let mut imodules: Vec<&dyn IdentificationModule> = Vec::new();
        let ex_imodules = orchestration::find_identification_modules(imodules_path);
        for eximod in &ex_imodules {
            imodules.push(eximod as &dyn IdentificationModule);
        }
        let mut emodules: Vec<Box<dyn ExplorationModule>> = Vec::new();
        let ex_emodules = orchestration::find_exploration_modules(emodules_path);
        for exemod in ex_emodules {
            emodules.push(Box::new(exemod) as Box<dyn ExplorationModule>);
        }

        let identified = orchestration::identification_procedure(run_path, &imodules);

        let dominant =
            compute_dominant_combinations(&identified_path, &solution_path, &identified, &emodules);

        for (i, (e, m)) in dominant.iter().enumerate() {
            idesyde_rust_core::write_model_header_to_path(
                m,
                &identified_path,
                i.to_string().as_str(),
                "Orchestrator",
            );
        }

        if let Some((exp, decision_model)) = dominant.first() {
            for sol in exp.explore(&identified_path, &solution_path, decision_model) {
                println!("A solved: {:?}", sol.header().category);
            }
        }
    } else {
        println!("At least one input design model is necessary")
    }
}
