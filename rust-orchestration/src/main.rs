use std::{cmp::Ordering, collections::HashSet, path::Path, sync::Arc};

use clap::Parser;
use env_logger::WriteStyle;
use idesyde_core::{
    DecisionModel, DesignModel, ExplorationBid, ExplorationSolution, Explorer, OpaqueDesignModel,
};
use idesyde_orchestration::{
    exploration::explore_cooperatively, identification::identification_procedure,
};
use log::{debug, info, warn, Level};
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
        help = "Inclusion rule of which explorers are to be kept during bidding. \nIf none is given, all are included."
    )]
    explorer: Vec<String>,

    #[arg(
        long,
        default_value = "0",
        help = "The maximum JVM heap size in bytes. Default is 0, which means no limit."
    )]
    jvm_max_heap: usize,

    #[arg(
        long,
        help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit",
        long_help = "Sets the desired maximum number of solutions. \nIf non-positive, there is no litmit. \nThe identification and integration stages are unnafected."
    )]
    x_max_solutions: Option<i64>,

    #[arg(
        long,
        help = "Sets the desired maximum number of iterations after each exploration improvement. \nIf non-positive, there is no litmit",
        long_help = "Sets the desired maximum number of iterations after each exploration improvement. \nIf non-positive, there is no litmit. \nThe identification and integration stages are unnafected."
    )]
    x_improvement_iterations: Option<i64>,

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
        help = "Specifies target optimisation objectives as goal. If none is specified, all possible optimisation objectives are goals."
    )]
    x_target_objectives: Vec<String>,

    // #[arg(
    //     long,
    //     help = "An URL for external modules that are not created and destroyed by the orchestrator. Currently supported schemas are: http."
    // )]
    // module: Option<Vec<String>>,
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
        // let output_path = Path::new(output_path_str);
        let inputs_path = &run_path.join("inputs");
        let modules_path = &std::env::current_dir()
            .expect("Failed to get working directory.")
            .join("modules");
        let identified_path = run_path.join("identified");
        let explored_path = &run_path.join("explored");
        let reverse_path = &run_path.join("reversed");

        std::fs::create_dir_all(run_path)
            .expect("Failed to create run path directory during identification.");
        std::fs::create_dir_all(inputs_path)
            .expect("Failed to create input directory during identification.");
        std::fs::create_dir_all(modules_path)
            .expect("Failed to create imodules directory during identification.");
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

        debug!("Initializing modules");
        // let mut imodules: Vec<Arc<dyn IdentificationModule>> = Vec::new();
        // let mut emodules: Vec<Arc<dyn ExplorationModule>> = Vec::new();
        let mut modules =
            idesyde_orchestration::find_modules_with_config(modules_path, args.jvm_max_heap);

        // add embedded modules
        modules.push(Arc::new(idesyde_common::make_module()));
        modules.push(Arc::new(idesyde_bridge_minizinc::make_module()));

        // add externally declared modules
        // if let Some(external_modules) = args.module {
        //     for url_str in external_modules {
        //         if let Ok(parsed_url) = url::Url::parse(url_str.as_str()) {
        //             modules.push(Arc::new(ExternalServerModule::from(
        //                 &parsed_url,
        //                 url_str.as_str(),
        //             )));
        //         }
        //     }
        // }

        for eximod in &modules {
            debug!(
                "Registered module with identifier {}",
                &eximod.unique_identifier()
            );
        }

        let explorers: Vec<Arc<dyn Explorer>> =
            modules.iter().flat_map(|x| x.explorers()).collect();

        for explorer in &explorers {
            debug!(
                "Registered explorer with identifier {}",
                explorer.unique_identifier()
            );
        }

        info!(
            "A total of {} modules and {} explorers were detected.",
            modules.len(),
            explorers.len()
        );

        // continue
        debug!("Reading and preparing input files");
        // add an "Opaque" design model header so that all modules are aware of the input models
        let design_models: Vec<Arc<dyn DesignModel>> = sorted_inputs
            .par_iter()
            .flat_map(|s| OpaqueDesignModel::try_from(Path::new(s)))
            .map(|s| Arc::new(s))
            .flat_map(|m| {
                if m.body_as_string().is_none() {
                    warn!(
                        "Failed to read and prepare input {}. Trying to proceed anyway.",
                        m.category()
                    );
                    None
                } else {
                    Some(m as Arc<dyn DesignModel>)
                }
            })
            .collect();
        for m in &design_models {
            m.write_to_dir(&inputs_path, "input", "Orchestratror");
        }
        let pre_identified: Vec<Arc<dyn DecisionModel>> = Vec::new();
        info!(
            "Starting identification with {} pre-identified decision models",
            pre_identified.len()
        );
        let identification_time = std::time::Instant::now();
        let (identified, _) =
            identification_procedure(&modules, &design_models, &pre_identified, 0);
        debug!(
            "Time spent identifying (ms): {}",
            identification_time.elapsed().as_millis()
        );
        info!("Identified {} decision model(s)", identified.len());
        debug!(
            "identified categories: {}",
            identified
                .iter()
                .map(|x| x.category())
                .reduce(|s1, s2| s1.clone() + ", " + &s2)
                .unwrap_or("None".to_string())
        );
        for (i, m) in identified.iter().enumerate() {
            m.write_to_dir(
                &identified_path,
                format!("final_{}", i).as_str(),
                "Orchestratror",
            );
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
        let bidding_time = std::time::Instant::now();
        let dominant_partial_identification =
            idesyde_core::compute_dominant_identification(&identified);
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
            .filter(|(e, _, _)| {
                args.explorer.len() == 0 || args.explorer.contains(&e.unique_identifier())
            })
            .collect();
        debug!(
            "Time spent bidding (ms): {}",
            bidding_time.elapsed().as_millis()
        );
        let dominant_biddings_idx: Vec<usize> = idesyde_core::compute_dominant_biddings(&biddings);
        info!(
            "Acquired {} dominant bidding(s) out of {} bidding(s)",
            dominant_biddings_idx.len(),
            biddings.len()
        );
        // let dominant_bidding_opt =
        //     idesyde_core::compute_dominant_bidding(biddings.iter().map(|(_, _, b)| b));
        let total_identifieable_elements: HashSet<String> = design_models
            .iter()
            .map(|x| x.elements())
            .flatten()
            .collect();
        if dominant_biddings_idx.len() > 0 {
            if !dominant_biddings_idx.iter().any(|i| {
                biddings[*i]
                    .1
                    .part()
                    .is_superset(&total_identifieable_elements)
            }) {
                warn!("No dominant bidding captures all partially identified elements. Double-check any final reversed models if any is produced. You can see the non-identified elements by setting using DEBUG verbosity.");
                debug!(
                    "Elements that are not covered are: {:?}",
                    total_identifieable_elements
                        .difference(
                            &dominant_biddings_idx
                                .iter()
                                .map(|i| biddings[*i].1.part())
                                .flatten()
                                .collect()
                        )
                        .map(|s| s.to_owned())
                        .reduce(|s1, s2| format!("{}, {}", s1, s2))
                        .unwrap_or("{}".to_string())
                );
            }
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
                dominant_biddings_idx
                    .iter()
                    .map(|i| biddings[*i].1.category()
                        + " with "
                        + &biddings[*i].0.unique_identifier())
                    .reduce(|a, b| a + " and " + &b)
                    .unwrap_or("No explorer".to_string())
            );
            let mut dominant_sols: Vec<ExplorationSolution> = vec![];
            let mut num_sols = 0;
            let exploration_time = std::time::Instant::now();
            let explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)> =
                dominant_biddings_idx
                    .iter()
                    .map(|i| (biddings[*i].0.to_owned(), biddings[*i].1.to_owned()))
                    .collect();
            let dominant_biddings: Vec<ExplorationBid> = dominant_biddings_idx
                .iter()
                .map(|i| biddings[*i].2.to_owned())
                .collect();
            let conf = idesyde_core::ExplorationConfigurationBuilder::default()
                .max_sols(args.x_max_solutions.unwrap_or(-1))
                .total_timeout(args.x_total_time_out.unwrap_or(0))
                .time_resolution(args.x_time_resolution.unwrap_or(0))
                .memory_resolution(args.x_memory_resolution.unwrap_or(0))
                .strict(args.strict)
                .improvement_timeout(args.x_improvement_time_out.unwrap_or(0))
                .improvement_iterations(args.x_improvement_iterations.unwrap_or(-1))
                .parallelism(args.parallel_jobs.unwrap_or(1) as u32)
                .target_objectives(
                    args.x_target_objectives
                        .iter()
                        .map(|x| x.to_string())
                        .collect(),
                )
                .build()
                .expect("Failed to build explorer configuration. Should never fail.");
            for sol in explore_cooperatively(
                explorers_and_models.as_slice(),
                dominant_biddings.as_slice(),
                &HashSet::new(),
                &conf,
            ) {
                // let sol_dominated = dominant_sols.iter().any(|(_, y)| {
                //     idesyde_core::pareto_dominance_partial_cmp(&sol.1, y) == Some(Ordering::Greater)
                // });
                if !dominant_sols.contains(&sol) {
                    debug!(
                        "New solution {} with objectives: {}.",
                        sol.solved.category(),
                        &sol.objectives
                            .iter()
                            .map(|(k, v)| format!("{}: {}", k, v))
                            .reduce(|s1, s2| format!("{}, {}", s1, s2))
                            .unwrap_or("None".to_owned())
                    );
                    dominant_sols
                        .retain(|cur_sol| sol.partial_cmp(cur_sol) != Some(Ordering::Less));
                    dominant_sols.push(sol.clone());
                    sol.solved.write_to_dir(
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
            debug!(
                "Time spent exploring (ms): {}",
                exploration_time.elapsed().as_millis()
            );
            info!(
                "Finished exploration with {} total and {} dominant solution(s)",
                num_sols,
                dominant_sols.len()
            );
            for (i, sol) in dominant_sols.iter().enumerate() {
                sol.solved
                    .write_to_dir(&explored_path, format!("{}", i).as_str(), "Orchestratror");
                debug!(
                    "Written dominant {} with objectives: {}",
                    sol.solved.category(),
                    sol.objectives
                        .iter()
                        .map(|(k, v)| format!("{}: {}", k, v))
                        .reduce(|s1, s2| format!("{}, {}", s1, s2))
                        .unwrap_or("None".to_owned())
                )
            }
            let solved_models: Vec<Arc<dyn DecisionModel>> = dominant_sols
                .iter()
                .map(|cur_sol| cur_sol.solved.clone())
                .collect();
            if !solved_models.is_empty() {
                info!("Starting reverse identification");
                let reverse_time = std::time::Instant::now();
                // note that the reverse identification is NOT done in parallel
                // this is because the current exploration implementation can stall a bit the rayon
                // threadpool, so that parallel iteration becomes slower than sequential;
                // plus, the reverse identification is usually a very small part of the whole process
                let all_reversed: usize = modules
                    .par_iter()
                    .map(|module| {
                        module
                            .reverse_identification_rules()
                            .par_iter()
                            .map(|rrule| {
                                let (models, msgs) =
                                    rrule.reverse_identify(&solved_models, &design_models);
                                for msg in msgs {
                                    debug!("{}", msg);
                                }
                                let mut n_reversed = 0;
                                for model in &models {
                                    model.write_to_dir(
                                        &reverse_path,
                                        format!("{}", n_reversed).as_str(),
                                        module.unique_identifier().as_str(),
                                    );
                                    n_reversed += 1;
                                    debug!(
                                        "Reverse identified a {} design model",
                                        model.category()
                                    );
                                }
                                n_reversed
                            })
                            .sum::<usize>()
                    })
                    .sum();
                debug!(
                    "Time spent reversing (ms): {}",
                    reverse_time.elapsed().as_millis()
                );
                info!(
                    "Finished reverse identification of {} design model(s)",
                    all_reversed
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
