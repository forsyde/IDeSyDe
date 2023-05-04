use std::{cmp::Ordering, fs, path::Path};

use clap::Parser;
use env_logger::WriteStyle;
use idesyde_core::{
    headers::{load_decision_model_headers_from_binary, load_design_model_headers_from_binary},
    DecisionModel, DesignModel, ExplorationModule, IdentificationModule,
};
use log::{debug, error, info, warn, Level};

use crate::orchestration::compute_dominant_combinations;

pub mod orchestration;

#[derive(Parser, Debug)]
#[command(
    name = "orchestrator",
    author = "Rodolfo Jordao",
    about = "Orchestration and entry point for XXxXxXx."
)]
struct Args {
    // input files
    #[arg(help = "The input design models that XXxXxXx will identify and explore.")]
    inputs: Vec<String>,

    #[arg(
        short,
        long,
        help = "Sets output file or directory.",
        long_help = "Sets output file or directory. \n\
         If the output path is a file, XXxXxXx will write the latest solved/optimized design model in this file. \n\
         If the output path is a directory, XXxXxXx will write all solved/optimized design model in this directory."
    )]
    output_path: Option<String>,

    #[arg(
        long,
        default_value = "run",
        help = "Sets the running path that XXxXxXx uses."
    )]
    run_path: Option<String>,

    #[arg(short, long, help = "Sets the verbosity of this run.")]
    verbosity: Option<String>,

    #[arg(
        long,
        help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit",
        long_help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit. \nThe identification and integration stages are unnafected."
    )]
    x_max_solutions: Option<i64>,

    #[arg(
        long,
        help = "Sets the _total exploration_ time-out in seconds. \nIf non-positive, there is no time-out.",
        long_help = "Sets the _total exploration_ time-out in seconds. \nIf non-positive, there is no time-out. \nThe identification and integration stages are unnafected."
    )]
    x_total_time_out: Option<i64>,

    #[arg(
        long,
        help = "For explorer with mandatory discretization, this factor is used for the time upsizing resolution."
    )]
    x_time_resolution: Option<i64>,

    #[arg(
        long,
        help = "For explorer with mandatory discretization, this factor is used for the memory downsizing resolution."
    )]
    x_memory_resolution: Option<i64>,
}

fn main() {
    let args = Args::parse();
    let verbosity = args
        .verbosity
        .and_then(|s| match s.to_lowercase().as_str() {
            "debug" => Some(Level::Debug),
            "warn" | "warning" => Some(Level::Warn),
            "err" | "error" => Some(Level::Error),
            "info" => Some(Level::Info),
            _ => None,
        })
        .unwrap_or(Level::Info);
    env_logger::Builder::new()
        .target(env_logger::Target::Stdout)
        .filter(None, verbosity.to_level_filter())
        .write_style(WriteStyle::Always)
        .format_target(false)
        .format_module_path(false)
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
        let reverse_path = &run_path.join("integrated");

        std::fs::create_dir_all(run_path)
            .expect("Failed to create run path directory during identification.");
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
        std::fs::create_dir_all(&reverse_path)
            .expect("Failed to create explored directory during identification.");

        debug!("Copying input files");
        for input in &sorted_inputs {
            let p = Path::new(input);
            if !p.is_file() {
                error!("Input {} does not exist or is not a file!", input);
                return;
            }
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
            &reverse_path,
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
        let design_model_headers = load_design_model_headers_from_binary(&inputs_path);
        let design_models: Vec<Box<dyn DesignModel>> = design_model_headers
            .iter()
            .map(|h| Box::new(h.to_owned()) as Box<dyn DesignModel>)
            .collect();
        let mut pre_identified: Vec<Box<dyn DecisionModel>> =
            load_decision_model_headers_from_binary(&identified_path)
                .iter()
                .map(|(_, h)| Box::new(h.to_owned()) as Box<dyn DecisionModel>)
                .collect();
        let identified =
            orchestration::identification_procedure(&imodules, &design_models, &mut pre_identified);
        info!("Identified {} decision model(s)", identified.len());
        let identified_refs = identified.iter().collect();

        // let dominant = compute_dominant_decision_models(&identified_refs);

        // for (p, m) in load_decision_model_headers_from_binary(&identified_path) {
        //     if dominant.iter().all(|dom| {
        //         let h = dom.header();
        //         h.partial_cmp(&m)
        //             .map(|x| match x {
        //                 Ordering::Greater => true,
        //                 _ => false,
        //             })
        //             .unwrap_or(false)
        //     }) {
        //         // m is domianted, proceed to delete its files
        //         if let Some(bp) = m.header().body_path {
        //             let jbp = Path::new(&bp).with_extension("json");
        //             match std::fs::remove_file(jbp) {
        //                 Err(_) => {
        //                     warn!("Tried removing JSON decision model body but failed",)
        //                 }
        //                 _ => (),
        //             };
        //             std::fs::remove_file(bp).expect("Failed to remove body path of dominated decision model during identification. This is a benign error. Continuing");
        //         }
        //         match std::fs::remove_file(Path::new(&p.with_extension("json"))) {
        //             Err(_) => warn!("Tried remove a JSON decision model header but failed. This is a benign error. Continuing"),
        //             _ => (),
        //         };
        //         std::fs::remove_file(p).expect(
        //             "Failed to remove header of dominated decision model during identification",
        //         );
        //     }
        // }

        let dominant_combinations = compute_dominant_combinations(&emodules, &identified_refs);
        info!(
            "Computed {} dominant explorer and decision model combination(s) ",
            dominant_combinations.len()
        );
        for (p, m) in load_decision_model_headers_from_binary(&identified_path) {
            for (_, dom) in &dominant_combinations {
                let to_be_deleted = match dom.header().partial_cmp(&m.header()) {
                    Some(Ordering::Greater) => true,
                    Some(Ordering::Equal) => {
                        match dom.header().body_path.partial_cmp(&m.header().body_path) {
                            Some(Ordering::Less) => true,
                            _ => false,
                        }
                    }
                    _ => false,
                };
                if to_be_deleted {
                    if let Some(bp) = m.header().body_path {
                        let jbp = Path::new(&bp).with_extension("json");
                        match std::fs::remove_file(jbp) {
                            Err(_) => {
                                warn!("Tried removing JSON decision model body but failed",)
                            }
                            _ => (),
                        };
                        match std::fs::remove_file(bp) {
                            Err(_) => warn!("Failed to remove body path of dominated decision model during identification. This is a benign error. Continuing"),
                            _ => (),
                        }
                    }
                    match std::fs::remove_file(Path::new(&p.with_extension("json"))) {
                       Err(_) => warn!("Tried remove a JSON decision model header but failed. This is a benign error. Continuing"),
                       _ => (),
                    };
                    std::fs::remove_file(&p).expect(
                        "Failed to remove header of dominated decision model during identification",
                    );
                }
            }
        }

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
        let mut sols_found = 0;
        if let Some((exp, decision_model)) = dominant_combinations.first() {
            for (i, sol) in exp
                .explore(
                    &decision_model,
                    args.x_max_solutions.unwrap_or(0),
                    args.x_total_time_out.unwrap_or(0),
                    args.x_time_resolution.unwrap_or(-1),
                    args.x_memory_resolution.unwrap_or(-1),
                )
                .enumerate()
            {
                sols_found += 1;
                let solv = vec![sol];
                debug!("Found a new solution. Total count is {}.", i + 1);
                for imodule in &imodules {
                    for reverse in imodule.reverse_identification(&solv, &design_models) {
                        idesyde_core::write_design_model_header_to_path(
                            &reverse.header(),
                            &reverse_path,
                            format!("{}_{}", "reversed_", i).as_str(),
                            "Orchestrator",
                        );
                    }
                }
            }
        }
        info!("Finished exploration with {} solution(s).", sols_found)
    } else {
        info!("At least one input design model is necessary")
    }
}
