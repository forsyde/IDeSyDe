use std::{cmp::Ordering, net::IpAddr, path::Path, sync::Arc};

use clap::Parser;
use env_logger::WriteStyle;
use idesyde_core::{
    headers::ExplorationBid, DecisionModel, DesignModel, ExplorationConfiguration,
    ExplorationSolution, Explorer,
};
use idesyde_orchestration::{
    exploration::{explore_cooperatively, ExternalServerExplorationModule},
    identification::{identification_procedure, ExternalServerIdentificationModule},
    models::OpaqueDesignModel,
};
use log::{debug, error, info, Level};
use rayon::prelude::*;

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
        short,
        long,
        help = "Sets the maximum number of parallel jobs (or threads) for the non-exploration procedures. Default is 1."
    )]
    parallel_jobs: Option<usize>,

    #[arg(
        long,
        help = "Inclusion rule of which decision models are to be kept during bidding. \nIf none is given, all are included."
    )]
    decision_model: Vec<String>,

    #[arg(
        long,
        help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit",
        long_help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit. \nThe identification and integration stages are unnafected."
    )]
    x_max_solutions: Option<u64>,

    #[arg(
        long,
        help = "Sets the desired maximum number of iterations after each exploration improvement. \nIf non-positive, there is no litmit",
        long_help = "Sets the desired maximum number of iterations after each exploration improvement. \nIf non-positive, there is no litmit. \nThe identification and integration stages are unnafected."
    )]
    x_improvement_iterations: Option<u64>,

    #[arg(
        long,
        help = "Sets the _total exploration_ time-out in seconds. \nIf non-positive, there is no time-out.",
        long_help = "Sets the _total exploration_ time-out in seconds. \nIf non-positive, there is no time-out. \nThe identification and integration stages are unnafected."
    )]
    x_total_time_out: Option<u64>,

    #[arg(
        long,
        help = "Sets the _improvement exploration_ time-out in seconds. That is, the maximum time allowed after a strict improvement is made during exploration. \nIf non-positive, there is no time-out.",
        long_help = "Sets the _improvement exploration_ time-out in seconds. That is, the maximum time allowed after a strict improvement is made during exploration. \nIf non-positive, there is no time-out. \nThe identification and integration stages are unnafected."
    )]
    x_improvement_time_out: Option<u64>,

    #[arg(
        long,
        help = "For explorer with mandatory discretization, this factor is used for the time upsizing resolution."
    )]
    x_time_resolution: Option<u64>,

    #[arg(
        long,
        help = "For explorer with mandatory discretization, this factor is used for the memory downsizing resolution."
    )]
    x_memory_resolution: Option<u64>,

    #[arg(
        long,
        help = "An URL for exploration modules that are not created and destroyed by the orchestrator. Currently supported protocols are: http."
    )]
    emodule: Option<Vec<String>>,

    #[arg(
        long,
        help = "An URL for identification modules that are not created and destroyed by the orchestrator. Currently supported protocols are: http."
    )]
    imodule: Option<Vec<String>>,

    #[arg(
        long,
        help = "If set, the exploration only returns solutions that improve the current Pareto set approximation."
    )]
    strict: bool,
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
        rayon::ThreadPoolBuilder::new()
            .num_threads(args.parallel_jobs.unwrap_or(1))
            .build_global()
            .unwrap();
        // let mut hasher = sha3::Sha3_224::new();
        let mut sorted_inputs = args.inputs.clone();
        sorted_inputs.sort();
        // for input in &sorted_inputs {
        //     if let Ok(f) = fs::read(input) {
        //         hasher.update(f.as_slice());
        //     }
        // }

        let run_path_str = &args.run_path.unwrap_or("run".to_string());
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
        let explored_path = &run_path.join("explored");
        let reverse_path = &run_path.join("reversed");

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
        std::fs::create_dir_all(&explored_path)
            .expect("Failed to create explored directory during identification.");
        std::fs::create_dir_all(&reverse_path)
            .expect("Failed to create explored directory during identification.");

        // let mut is_input_incremental = false;
        // let mut current_input_stamp: Vec<(String, u64)> = sorted_inputs
        //     .iter()
        //     .map(|x| {
        //         (
        //             x.to_owned(),
        //             std::fs::metadata(std::path::PathBuf::from(x))
        //                 .ok()
        //                 .and_then(|y| y.modified().ok())
        //                 .and_then(|y| y.duration_since(UNIX_EPOCH).ok())
        //                 .map(|y| y.as_secs())
        //                 .unwrap_or(0),
        //         )
        //     })
        //     .collect();
        // if let Ok(f) = std::fs::read(run_path.join("input_stamp.json")) {
        //     let previous_input_stream: Vec<(String, u64)> =
        //         serde_json::from_slice(&f).unwrap_or(Vec::new());
        //     for x in &current_input_stamp {
        //         if !previous_input_stream.contains(x) {
        //             is_input_incremental = false;
        //             break;
        //         }
        //     }
        //     for x in previous_input_stream {
        //         if !current_input_stamp.contains(&x) {
        //             current_input_stamp.push(x);
        //         }
        //     }
        // }

        // if !is_input_incremental {
        //     debug!("Detected that the inputs are not incremental. Cleaning the workspace before proceeding");
        //     for dir in vec![inputs_path, &identified_path, &explored_path, &reverse_path] {
        //         if let Ok(d) = std::fs::read_dir(dir) {
        //             for x in d {
        //                 if let Ok(f) = x {
        //                     if std::fs::remove_file(f.path()).is_err() {
        //                         warn!("Failed to remove workspace file during incremental reset. Trying to proceed");
        //                     };
        //                 }
        //             }
        //         };
        //     }
        // }
        // if let Ok(f) = std::fs::File::create(run_path.join("input_stamp.json")) {
        //     if serde_json::to_writer(f, &current_input_stamp).is_err() {
        //         warn!("Failed to produce increment stamps. Incremetability might not work")
        //     };
        // } else {
        //     warn!("Failed to create increment stamps. Incremetability might not work")
        // }

        // debug!("Reading and preparing input files");
        // for input in &sorted_inputs {
        //     let p = Path::new(input);
        //     if !p.is_file() {
        //         error!("Input {} does not exist or is not a file!", input);
        //         return;
        //     }
        //     if let Some(fname) = p.file_name() {
        //         let fpath = Path::new(fname);
        //         std::fs::copy(p, run_path.join("inputs").join(fpath))
        //             .expect("Failed to copy input models during identification.");
        //     }
        // }

        debug!("Initializing modules");
        // let mut imodules: Vec<Arc<dyn IdentificationModule>> = Vec::new();
        // let mut emodules: Vec<Arc<dyn ExplorationModule>> = Vec::new();
        let (mut imodules, mut emodules) = rayon::join(
            || {
                idesyde_orchestration::find_identification_modules(
                    imodules_path,
                    &identified_path,
                    &inputs_path,
                    &explored_path,
                    &reverse_path,
                    &output_path,
                )
            },
            || idesyde_orchestration::find_exploration_modules(emodules_path),
        );

        // add embedded modules
        imodules.push(Arc::new(idesyde_common::make_common_module()));

        // add externally declared modules
        if let Some(external_modules) = args.imodule {
            for url in external_modules {
                if url.starts_with("http://") {
                    let mut splitted = url[7..].split(":");
                    if let Some(ip) = splitted.next().and_then(|x| x.parse::<IpAddr>().ok()) {
                        match ip {
                            IpAddr::V4(ipv4) => {
                                imodules.push(Arc::new(ExternalServerIdentificationModule::from(
                                    ipv4.to_string().as_str(),
                                    &ipv4,
                                    splitted
                                        .next()
                                        .and_then(|p| p.parse::<usize>().ok())
                                        .unwrap_or(80usize),
                                )))
                            }
                            _ => (),
                        }
                    }
                }
            }
        }

        if let Some(external_modules) = args.emodule {
            for url in external_modules {
                if url.starts_with("http://") {
                    let mut splitted = url[7..].split(":");
                    if let Some(ip) = splitted.next().and_then(|x| x.parse::<IpAddr>().ok()) {
                        match ip {
                            IpAddr::V4(ipv4) => {
                                emodules.push(Arc::new(ExternalServerExplorationModule::from(
                                    ipv4.to_string().as_str(),
                                    &ipv4,
                                    splitted
                                        .next()
                                        .and_then(|p| p.parse::<usize>().ok())
                                        .unwrap_or(80usize),
                                )))
                            }
                            _ => (),
                        }
                    }
                }
            }
        }

        for eximod in &imodules {
            debug!(
                "Registered identification module with identifier {}",
                &eximod.unique_identifier()
            );
        }
        for exemod in &emodules {
            debug!(
                "Registered exploration module with identifier {}",
                &exemod.unique_identifier()
            );
        }

        // continue
        debug!("Reading and preparing input files");
        // let design_model_headers = load_design_model_headers_from_binary(&inputs_path);
        // let mut design_models: Vec<Box<dyn DesignModel>> = design_model_headers
        //     .iter()
        //     .map(|h| Box::new(h.to_owned()) as Box<dyn DesignModel>)
        //     .collect();
        // add an "Opaque" design model header so that all modules are aware of the input models
        let design_models: Vec<Arc<dyn DesignModel>> = sorted_inputs
            .iter()
            .map(|s| Arc::new(OpaqueDesignModel::from_path_str(s)) as Arc<dyn DesignModel>)
            .collect();
        for m in &design_models {
            if m.body_as_string().is_none() {
                error!(
                    "Failed to read and prepare input {}",
                    m.header()
                        .model_paths
                        .first()
                        .map(|x| x.to_owned())
                        .unwrap_or("NO_FILE".to_string())
                );
                return;
            }
        }
        // design_models.push(Box::new(DesignModelHeader {
        //     category: "Any".to_string(),
        //     model_paths: args.inputs,
        //     elements: HashSet::new(),
        // }));
        let pre_identified: Vec<Arc<dyn DecisionModel>> = vec![];
        // load_decision_model_headers_from_binary(&identified_path)
        //     .iter()
        //     .map(|(_, h)| Arc::new(OpaqueDecisionModel::from(h)) as Arc<dyn DecisionModel>)
        //     .collect();
        info!(
            "Starting identification with {} pre-identified decision models",
            pre_identified.len()
        );
        let (identified, _) =
            identification_procedure(&imodules, &design_models, &pre_identified, 0);
        info!("Identified {} decision model(s)", identified.len());
        debug!(
            "identified categories: {}",
            identified
                .iter()
                .map(|x| x.category())
                .reduce(|s1, s2| s1.clone() + ", " + &s2)
                .unwrap_or("None".to_string())
        );
        for (_, m) in identified.iter().enumerate() {
            m.write_to_dir(&identified_path, "final", "Orchestratror");
        }
        // println!(
        //     "{}",
        //     identified
        //         .iter()
        //         .map(|x| x.body_as_json().unwrap_or("NONE".to_string()))
        //         .reduce(|s1, s2| s1.clone() + ";\n " + &s2)
        //         .unwrap_or("None".to_string())
        // );

        // let dominant = compute_dominant_decision_models(&identified_refs);

        // let dominant_without_biddings = compute_dominant_decision_models(&identified_refs);
        let dominant_partial_identification =
            idesyde_core::compute_dominant_identification(&identified);
        let explorers: Vec<Arc<dyn Explorer>> =
            emodules.iter().flat_map(|x| x.explorers()).collect();
        let biddings: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>, ExplorationBid)> = explorers
            .iter()
            .flat_map(|explorer| {
                dominant_partial_identification
                    .iter()
                    .map(|x| (explorer.clone(), x.clone(), explorer.bid(x.clone())))
            })
            .filter(|(_, _, b)| b.can_explore)
            .filter(|(_, m, _)| {
                args.decision_model.len() == 0 || args.decision_model.contains(&m.category())
            })
            .collect();
        info!("Computed {} bidding(s) ", biddings.len());
        // let dominant_bidding_opt =
        //     idesyde_core::compute_dominant_bidding(biddings.iter().map(|(_, _, b)| b));
        let dominant_biddings = idesyde_core::compute_dominant_biddings(
            &biddings.iter().map(|(_, _, b)| b.to_owned()).collect(),
        );
        if dominant_biddings.len() > 0 {
            match (args.x_total_time_out, args.x_max_solutions) {
                (Some(t), Some(n)) => info!(
                    "Starting exploration up to {} total time-out seconds and {} solution(s)",
                    t, n
                ),
                (Some(t), None) => {
                    info!("Starting exploration up to {} total time-out second(s)", t)
                }
                (None, Some(n)) => info!("Starting exploration up to {} solution(s)", n),
                (None, None) => info!("Starting exploration until completion"),
            }
            // let (mut tx, rx) = spmc::channel();
            // let mut total_reversed = 0;
            debug!(
                "Proceeding to explore {}",
                dominant_biddings
                    .iter()
                    .map(|(i, x)| biddings[*i].1.category()
                        + " with "
                        + &x.explorer_unique_identifier.to_owned())
                    .reduce(|a, b| a + " and " + &b)
                    .unwrap_or("No explorer".to_string())
            );
            let mut dominant_sols: Vec<ExplorationSolution> = vec![];
            let mut num_sols = 0;
            for sol in explore_cooperatively(
                dominant_biddings
                    .iter()
                    .map(|(i, _)| (biddings[*i].0.to_owned(), biddings[*i].1.to_owned()))
                    .collect(),
                Vec::new(),
                idesyde_core::ExplorationConfigurationBuilder::new()
                    .max_sols(args.x_max_solutions.unwrap_or(0))
                    .total_timeout(args.x_total_time_out.unwrap_or(0))
                    .time_resolution(args.x_time_resolution.unwrap_or(0))
                    .memory_resolution(args.x_memory_resolution.unwrap_or(0))
                    .strict(args.strict)
                    .improvement_timeout(args.x_improvement_time_out.unwrap_or(0))
                    .improvement_iterations(args.x_improvement_iterations.unwrap_or(0))
                    .build(),
            ) {
                // let sol_dominated = dominant_sols.iter().any(|(_, y)| {
                //     idesyde_core::pareto_dominance_partial_cmp(&sol.1, y) == Some(Ordering::Greater)
                // });
                if !dominant_sols.contains(&sol) {
                    debug!(
                        "New solution with objectives: {}.",
                        &sol.1
                            .iter()
                            .map(|(k, v)| format!("{}: {}", k, v))
                            .reduce(|s1, s2| format!("{}, {}", s1, s2))
                            .unwrap_or("None".to_owned())
                    );
                    dominant_sols.retain(|(_, y)| {
                        idesyde_core::pareto_dominance_partial_cmp(&sol.1, y)
                            != Some(Ordering::Less)
                    });
                    dominant_sols.push(sol.clone());
                    sol.0.write_to_dir(
                        &explored_path,
                        format!("{}_intermediate", num_sols).as_str(),
                        "Orchestratror",
                    );
                    num_sols += 1;
                    if args.x_max_solutions.unwrap_or(0) > 0
                        && num_sols >= args.x_max_solutions.unwrap_or(0)
                    {
                        break;
                    }
                }
                // imodules.par_iter().for_each(|imodule| {
                //     for reverse in
                //         imodule.reverse_identification(&vec![sol.0.clone()], &design_models)
                //     {
                //         // let reverse_header = reverse.header();
                //         reverse.write_to_dir(
                //             &reverse_path,
                //             format!("{}_intermediate", num_sols).as_str(),
                //             "Orchestrator",
                //         );
                //         debug!("Reverse identified a {} design model", reverse.category());
                //     }
                // });
            }
            // sols_found.dedup_by(|(_, a), (_, b)| a == b);
            // let dominant_sols: Vec<ExplorationSolution> = sols_found
            //     .iter()
            //     .filter(|x @ (_, objs)| {
            //         sols_found.iter().filter(|y| x != y).all(|(_, other_objs)| {
            //             objs.iter().any(|(k, v)| v < other_objs.get(k).unwrap())
            //         })
            //     })
            //     .map(|x| x.to_owned())
            //     .collect();
            info!(
                "Finished exploration with {} total and {} dominant solution(s)",
                num_sols,
                dominant_sols.len()
            );
            for (i, (m, objs)) in dominant_sols.iter().enumerate() {
                m.write_to_dir(&explored_path, format!("{}", i).as_str(), "Orchestratror");
                debug!(
                    "Written dominant with objectives: {}",
                    objs.iter()
                        .map(|(k, v)| format!("{}: {}", k, v))
                        .reduce(|s1, s2| format!("{}, {}", s1, s2))
                        .unwrap_or("None".to_owned())
                )
            }
            let solved_models: Vec<Arc<dyn DecisionModel>> =
                dominant_sols.iter().map(|(x, _)| x.clone()).collect();
            if !dominant_sols.is_empty() {
                info!("Starting integration");
                let total_reversed: usize = imodules
                    .par_iter()
                    .enumerate()
                    .map(|(_, imodule)| {
                        let mut n_reversed = 0;
                        for reverse in
                            imodule.reverse_identification(&solved_models, &design_models)
                        {
                            // let reverse_header = reverse.header();
                            reverse.write_to_dir(
                                &reverse_path,
                                format!("{}", n_reversed).as_str(),
                                "Orchestrator",
                            );
                            n_reversed += 1;
                            debug!("Reverse identified a {} design model", reverse.category());
                        }
                        n_reversed
                    })
                    .sum();
                info!(
                    "Finished reverse identification of {} design model(s)",
                    total_reversed
                );
            } else {
                info!("No solution to reverse identify");
            }
        } else {
            info!("No dominant bidding to start exploration. Finished")
        }
    } else {
        info!("At least one input design model is necessary")
    }
}
