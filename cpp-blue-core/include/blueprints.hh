#if !defined(H_BLUEPRINTS)
#define H_BLUEPRINTS

#include <core.hh>
#include <nlohmann/json.hpp>
#include <cxxopts.hpp>
#include <iostream>

namespace idesyde::blueprints
{

    class StandaloneIdentificationModule : idesyde::core::IdentificationModule
    {
    public:
        virtual shared_ptr<idesyde::core::DecisionModel> read_design_model(filesystem::path path);
        virtual bool write_design_model(shared_ptr<idesyde::core::DecisionModel> design_model, filesystem::path dest);
        virtual optional<shared_ptr<idesyde::core::DecisionModel>> decision_header_to_model(idesyde::headers::DecisionModelHeader header);
        virtual vector<idesyde::core::IdentificationRule> identification_rules();
        virtual vector<idesyde::core::ReverseIdentificationRule> reverse_identification_rules();
        vector<shared_ptr<idesyde::core::DecisionModel>> identification_step(
            uint32_t iteration,
            vector<shared_ptr<idesyde::core::DesignModel>> design_models,
            vector<shared_ptr<idesyde::core::DecisionModel>> decision_models)
        {
            vector<shared_ptr<idesyde::core::DecisionModel>> identified;
            for (auto irule : this->identification_rules())
            {
                for (auto new_model : irule.identify(design_models, decision_models))
                {
                    // check if the collection contains it or not
                    if (find(identified.begin(), identified.end(), new_model) != identified.end())
                    {
                        identified.push_back(new_model);
                    }
                }
            }
            return identified;
        };
        vector<shared_ptr<idesyde::core::DesignModel>> reverse_identification(
            vector<shared_ptr<idesyde::core::DecisionModel>> decision_models,
            vector<shared_ptr<idesyde::core::DesignModel>> design_models)
        {
            vector<shared_ptr<idesyde::core::DesignModel>> reverse_identified;
            for (auto irule : this->reverse_identification_rules())
            {
                for (auto new_model : irule.reverse_identify(decision_models, design_models))
                {
                    // check if the collection contains it or not
                    if (find(reverse_identified.begin(), reverse_identified.end(), new_model) != reverse_identified.end())
                    {
                        reverse_identified.push_back(new_model);
                    }
                }
            }
            return reverse_identified;
        };
        int standalone_identification_module(int argc, char **argv)
        {
            return 0;
        };
    };

    class StandaloneExplorationModule : idesyde::core::ExplorationModule
    {
    public:
        virtual optional<shared_ptr<idesyde::core::DecisionModel>> decision_header_to_model(idesyde::headers::DecisionModelHeader header);
        int standalone_exploration_module(int argc, char **argv)
        {
            cxxopts::Options options(this->unique_identifier(), "Exploration Module");
            options.add_options()("h,help", "Print usage")("i,decision-path", "Path for the identified decision models.", cxxopts::value<std::string>())("o,solution-path", "Path for the explored decision models.", cxxopts::value<std::string>())("e,explore", "Path to the decision model to be explored.", cxxopts::value<std::string>())("c,combine", "Path to the decision model to be bid.", cxxopts::value<std::string>())("total-timeout", "Total maximum time allowed.", cxxopts::value<long>()->default_value(0))("maximum-solutions", "Maximum number of solutions, including sub-optimals.", cxxopts::value<long>()->default_value(0))("time-resolution", "Resolution for time discretization in some problems.", cxxopts::value<long>()->default_value(0))("memory-resolution", "Resolution for memory discretization in some problems.", cxxopts::value<long>()->default_value(0));
            auto parse_result = options.parse(argc, argv);
            if (parse_result.count("i") && parse_result.count("c"))
            {
                std::filesystem::path p(parse_result["i"].as<std::string>());
                auto parsed_opt = idesyde::headers::header_from_path(p);
                if (parsed_opt.has_value())
                {
                    auto decision_model_opt = this->decision_header_to_model(parsed_opt.value());
                    if (decision_model_opt.has_value())
                    {
                        auto b = this->bid(decision_model_opt.value());
                        nlohmann::json j = b;
                        std::cout << j;
                        return 0;
                    }
                }
            }
            //         let args = IdentificationModuleArgs::parse();
            // if args.print_schema {
            //     for schema in &module.decision_models_schemas() {
            //         println!("{}", schema);
            //     }
            // } else {
            //     if let Some(design_path) = args.design_path_opt {
            //         std::fs::create_dir_all(&design_path)
            //             .expect("Failed to create the design path during reverse identification.");
            //         let mut design_models: Vec<Box<dyn DesignModel>> = Vec::new();
            //         for pres in std::fs::read_dir(&design_path)
            //             .expect("Failed to read design path during start-up.")
            //         {
            //             let p = pres.expect("Failed to read directory entry during start-up");
            //             if let Some(m) = module.read_design_model(&p.path()) {
            //                 let mut h = m.header();
            //                 h.model_paths.push(
            //                     p.path()
            //                         .to_str()
            //                         .expect("Failed to get OS string during start-up")
            //                         .to_string(),
            //                 );
            //                 write_design_model_header_to_path(&h, &design_path, "", &module.uid());
            //                 design_models.push(m);
            //             }
            //         }
            //         match (
            //             args.identified_path_opt,
            //             args.solved_path_opt,
            //             args.reverse_path_opt,
            //             args.identification_step,
            //         ) {
            //             (_, Some(solved_path), Some(reverse_path), _) => {
            //                 std::fs::create_dir_all(&solved_path)
            //                     .expect("Failed to create the solved path during reverse identification.");
            //                 std::fs::create_dir_all(&reverse_path)
            //                     .expect("Failed to create the reverse path during reverse identification.");
            //                 let solved: Vec<Box<dyn DecisionModel>> =
            //                     load_decision_model_headers_from_binary(&solved_path)
            //                         .iter()
            //                         .flat_map(|(_, x)| module.decision_header_to_model(x))
            //                         .collect();
            //                 let reverse_identified = module.reverse_identification(&solved, &design_models);
            //                 for m in reverse_identified {
            //                     let mut h = m.header();
            //                     if let Some(out_path) = &args.output_path_opt {
            //                         if module.write_design_model(&m, out_path) {
            //                             h.model_paths.push(out_path.to_str().expect("Failed to get a string out of the output path during reverse identification").to_string());
            //                         };
            //                     }
            //                     write_design_model_header_to_path(
            //                         &h,
            //                         &reverse_path,
            //                         "",
            //                         module.unique_identifier().as_str(),
            //                     );
            //                 }
            //             }
            //             (Some(identified_path), None, None, Some(ident_step)) => {
            //                 std::fs::create_dir_all(&identified_path).expect(
            //                     "Failed to create the identified path during reverse identification.",
            //                 );
            //                 let decision_models: Vec<Box<dyn DecisionModel>> =
            //                     load_decision_model_headers_from_binary(&identified_path)
            //                         .iter()
            //                         .flat_map(|(_, x)| module.decision_header_to_model(x))
            //                         .collect();
            //                 let identified =
            //                     module.identification_step(ident_step, &design_models, &decision_models);
            //                 for m in identified {
            //                     write_decision_model_to_path(
            //                         &m,
            //                         &identified_path,
            //                         format!("{:0>16}", ident_step).as_str(),
            //                         module.unique_identifier().as_str(),
            //                     );
            //                 }
            //             }
            //             _ => (),
            //         }
            //     }
            return 0;
        }
    };

} // namespace idesyde::blueprints

#endif // H_BLUEPRINTS
