use std::path::PathBuf;

use clap::Parser;
use idesyde_core::{
    headers::load_decision_model_headers_from_binary, DecisionModel, DesignModel,
    IdentificationModule, StandaloneIdentificationModule,
};

#[derive(Parser, Debug)]
#[command(author = "Rodolfo Jordao")]
pub struct IdentificationModuleArgs {
    #[arg(
        short = 'm',
        long = "design-path",
        help = "The path where the design models (and headers) are stored."
    )]
    design_path_opt: Option<PathBuf>,
    #[arg(
        short = 'i',
        long = "identified-path",
        help = "The path where identified decision models (and headers) are stored."
    )]
    identified_path_opt: Option<PathBuf>,
    #[arg(
        short = 's',
        long = "solved-path",
        help = "The path where explored decision models (and headers) are stored."
    )]
    solved_path_opt: Option<PathBuf>,
    #[arg(
        short = 'r',
        long = "reverse-path",
        help = "The path where integrated design models (and headers) are stored."
    )]
    reverse_path_opt: Option<PathBuf>,
    #[arg(
        short = 'o',
        long = "output-path",
        help = "The path where final integrated design models are stored, in their original format."
    )]
    output_path_opt: Option<PathBuf>,
    #[arg(short = 't', help = "The overall identification iteration number.")]
    identification_step: Option<i32>,
    #[arg(
        long = "schemas",
        help = "Prints decision model schemas from this module.",
        default_value = "false"
    )]
    print_schema: bool,
}

pub fn execute_standalone_identification_module(module: StandaloneIdentificationModule) {
    match IdentificationModuleArgs::try_parse() {
        Ok(args) => {
            if args.print_schema {
                for schema in &module.decision_model_schemas {
                    println!("{}", schema);
                }
            } else {
                if let Some(design_path) = args.design_path_opt {
                    std::fs::create_dir_all(&design_path)
                        .expect("Failed to create the design path during reverse identification.");
                    let mut design_models: Vec<Box<dyn DesignModel>> = Vec::new();
                    for pres in std::fs::read_dir(&design_path)
                        .expect("Failed to read design path during start-up.")
                    {
                        let p = pres.expect("Failed to read directory entry during start-up");
                        if let Some(m) = module.read_design_model(&p.path()) {
                            let mut h = m.header();
                            h.model_paths.push(
                                p.path()
                                    .to_str()
                                    .expect("Failed to get OS string during start-up")
                                    .to_string(),
                            );
                            h.write_to_dir(&design_path, "", &module.unique_identifier());
                            design_models.push(m);
                        }
                    }
                    match (
                        args.identified_path_opt,
                        args.solved_path_opt,
                        args.reverse_path_opt,
                        args.identification_step,
                    ) {
                        (_, Some(solved_path), Some(reverse_path), _) => {
                            std::fs::create_dir_all(&solved_path).expect(
                                "Failed to create the solved path during reverse identification.",
                            );
                            std::fs::create_dir_all(&reverse_path).expect(
                                "Failed to create the reverse path during reverse identification.",
                            );
                            let solved: Vec<Box<dyn DecisionModel>> =
                                load_decision_model_headers_from_binary(&solved_path)
                                    .iter()
                                    .flat_map(|(_, x)| module.decision_header_to_model(x))
                                    .collect();
                            let reverse_identified =
                                module.reverse_identification(&solved, &design_models);
                            for m in reverse_identified {
                                for rpath in module.write_design_model(&m, &reverse_path) {
                                    let mut h = m.header();
                                    h.model_paths.push(rpath.to_str().expect("Failed to get a string out of the output path during reverse identification").to_string());
                                    h.write_to_dir(
                                        &reverse_path,
                                        "",
                                        module.unique_identifier().as_str(),
                                    );
                                }
                                if let Some(out_path) = &args.output_path_opt {
                                    module.write_design_model(&m, out_path);
                                }
                            }
                        }
                        (Some(identified_path), None, None, Some(ident_step)) => {
                            std::fs::create_dir_all(&identified_path).expect(
                                "Failed to create the identified path during reverse identification.",
                            );
                            let decision_models: Vec<Box<dyn DecisionModel>> =
                                load_decision_model_headers_from_binary(&identified_path)
                                    .iter()
                                    .flat_map(|(_, x)| module.decision_header_to_model(x))
                                    .collect();
                            let identified = module.identification_step(
                                ident_step,
                                &design_models,
                                &decision_models,
                            );
                            for m in identified {
                                m.write_to_dir(
                                    &identified_path,
                                    format!("{:0>16}", ident_step).as_str(),
                                    module.unique_identifier().as_str(),
                                );
                            }
                        }
                        _ => (),
                    }
                }
            }
        }
        Err(_) => {
            println!("Incorrect combination of parameters/options. Check usage with -h/--help.");
            std::process::exit(64);
        }
    }
}
