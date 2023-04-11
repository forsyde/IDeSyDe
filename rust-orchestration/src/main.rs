use std::{fs, path::Path};

use clap::Parser;
use env_logger::WriteStyle;
use idesyde_core::{DecisionModel, DesignModel, ExplorationModule, IdentificationModule};
use log::{debug, info};

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
    env_logger::Builder::new()
        .target(env_logger::Target::Stdout)
        .filter(None, log::LevelFilter::Debug)
        .write_style(WriteStyle::Always)
        .init();
    if args.inputs.len() > 0 {
        // let mut hasher = sha3::Sha3_224::new();
        let mut sorted_inputs = args.inputs.clone();
        sorted_inputs.sort();
        // for input in &sorted_inputs {
        //     if let Ok(f) = fs::read(input) {
        //         hasher.update(f.as_slice());
        //     }
        // }

        let run_path_str = &args
            .run_path
            .expect("Failed to get run path durin initialization.");
        info!("Run directory is {}", &run_path_str);
        let output_path_str = &args
            .output_path
            .unwrap_or("explored_and_integrated.fiodl".to_string());
        info!("Final output set to {}", output_path_str);

        let run_path = Path::new(run_path_str);
        let output_path = Path::new(output_path_str);
        let inputs_path = &run_path.join("inputs");
        let imodules_path = &std::env::current_dir()
            .expect("Failed to get working directory.")
            .join("imodules");
        let emodules_path = &std::env::current_dir()
            .expect("Failed to get working directory.")
            .join("emodules");
        let identified_path = run_path.join("identified");
        let solution_path = &run_path.join("explored");
        let integration_path = &run_path.join("integrated");

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
        std::fs::create_dir_all(&integration_path)
            .expect("Failed to create explored directory during identification.");

        debug!("Copying input files");
        for input in &sorted_inputs {
            let p = Path::new(input);
            if let Some(fname) = p.file_name() {
                let fpath = Path::new(fname);
                fs::copy(p, run_path.join("inputs").join(fpath))
                    .expect("Failed to copy input models during identification.");
            }
        }

        let mut imodules: Vec<Box<dyn IdentificationModule>> = Vec::new();
        let ex_imodules = orchestration::find_and_prepare_identification_modules(
            imodules_path,
            &identified_path,
            &inputs_path,
            &solution_path,
            &integration_path,
            &output_path,
        );
        for eximod in ex_imodules {
            debug!(
                "Registering external identification module with identifier {}",
                &eximod.unique_identifier()
            );
            imodules.push(Box::new(eximod) as Box<dyn IdentificationModule>);
        }
        let mut emodules: Vec<Box<dyn ExplorationModule>> = Vec::new();
        let ex_emodules = orchestration::find_exploration_modules(
            emodules_path,
            &identified_path,
            &solution_path,
        );
        for exemod in ex_emodules {
            debug!(
                "Registering external exploration module with identifier {}",
                &exemod.unique_identifier()
            );
            emodules.push(Box::new(exemod) as Box<dyn ExplorationModule>);
        }

        // a zero-step to make design model headers available
        for imodule in &imodules {
            imodule.identification_step(0, &Vec::new(), &Vec::new());
        }
        // now we can proceed safely
        let design_model_headers =
            orchestration::load_design_model_headers_from_binary(&inputs_path);
        let design_models: Vec<Box<dyn DesignModel>> = design_model_headers
            .iter()
            .map(|h| Box::new(h.to_owned()) as Box<dyn DesignModel>)
            .collect();
        let mut identified: Vec<Box<dyn DecisionModel>> =
            orchestration::load_decision_model_headers_from_binary(&identified_path)
                .iter()
                .map(|(_, h)| Box::new(h.to_owned()) as Box<dyn DecisionModel>)
                .collect();
        let mut new_identified =
            orchestration::identification_procedure(&imodules, &design_models, &identified);
        identified.append(&mut new_identified);
        info!("Identified {} decision model(s)", identified.len());

        let dominant = compute_dominant_combinations(&emodules, &identified);
        info!(
            "Computed {} dominant explorer and decision model combination(s) ",
            dominant.len()
        );

        // for (i, (e, m)) in dominant.iter().enumerate() {
        //     idesyde_core::write_decision_model_header_to_path(
        //         m,
        //         &identified_path,
        //         format!("{}_{}", "dominant_", i).as_str(),
        //         "Orchestrator",
        //     );
        // }
        match (args.x_total_time_out, args.x_max_solutions) {
            (Some(t), Some(n)) => info!(
                "Starting exploration up to {} total time-out seconds and {} solutions.",
                t, n
            ),
            (Some(t), None) => info!("Starting exploration up to {} total time-out seconds.", t),
            (None, Some(n)) => info!("Starting exploration up to {} solutions.", n),
            (None, None) => info!("Starting exploration until completion."),
        }
        if let Some((exp, decision_model)) = dominant.first() {
            for (i, sol) in exp.explore(&decision_model, 0, 0).enumerate() {
                debug!("Found a new solution. Total count is {}.", i + 1);
                for imodule in &imodules {
                    for design_model in &design_models {
                        for integrated in imodule.reverse_identification(&design_model, &sol) {
                            idesyde_core::write_design_model_header_to_path(
                                &integrated,
                                &integration_path,
                                format!("{}_{}", "integrated_", i).as_str(),
                                "Orchestrator",
                            );
                        }
                    }
                }
            }
        }
    } else {
        info!("At least one input design model is necessary")
    }
}
