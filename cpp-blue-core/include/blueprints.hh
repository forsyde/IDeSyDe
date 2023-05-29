#if !defined(H_BLUEPRINTS)
#define H_BLUEPRINTS

#include <core.hh>

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
        int standalone_identification_module(int argc, char **argv)
        {
            return 0;
        };
    };

    class StandaloneExplorationModule : idesyde::core::ExplorationModule
    {
    public:
        int standalone_exploration_module(int argc, char **argv)
        {
            return 0;
        }
    };

} // namespace idesyde::blueprints

#endif // H_BLUEPRINTS
