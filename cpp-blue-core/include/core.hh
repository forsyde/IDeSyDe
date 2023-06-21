#if !defined(CORE_H)
#define CORE_H

#include <optional>
#include <functional>

#include <nlohmann/json.hpp>

#include <headers.hh>

using namespace std;

namespace idesyde::core
{

    class DecisionModel
    {
    public:
        virtual string unique_identifier();
        virtual idesyde::headers::DecisionModelHeader header();

        std::optional<string> body_as_json()
        {
            return std::nullopt;
        };

        std::optional<uint8_t[]> body_as_cbor()
        {
            return std::nullopt;
        };

        bool dominates(DecisionModel &other)
        {
            auto h = header();
            auto oh = other.header();
            for (auto &ov : oh.get_covered_elements())
            {
                bool found = false;
                for (auto &v : h.get_covered_elements())
                {
                    found = found || (v == ov);
                }
                if (!found)
                    return false;
            }
            return true;
        }
    };

    class DesignModel
    {
    public:
        virtual string unique_identifier();
        virtual idesyde::headers::DesignModelHeader header();
    };

    class IdentificationRule
    {
    public:
        virtual vector<shared_ptr<DecisionModel>> identify(vector<shared_ptr<DesignModel>> design_models,
                                                           vector<shared_ptr<DecisionModel>> decision_models);
        bool identifies_design_models() { return true; };
        bool identifies_decision_models() { return true; };
    };

    class ReverseIdentificationRule
    {
    public:
        virtual vector<shared_ptr<DesignModel>> reverse_identify(vector<shared_ptr<DecisionModel>> decision_models, vector<shared_ptr<DesignModel>> design_models);
    };

    class IdentificationModule
    {
    public:
        virtual string unique_identifier();
        virtual vector<shared_ptr<DecisionModel>> identification_step(
            uint32_t iteration,
            vector<shared_ptr<DesignModel>> design_models,
            vector<shared_ptr<DecisionModel>> decision_models);
        virtual vector<shared_ptr<DesignModel>> reverse_identification(
            vector<shared_ptr<DecisionModel>> decision_models,
            vector<shared_ptr<DesignModel>> design_models);
    };

    class ExplorationModule
    {
    public:
        virtual string unique_identifier();
        virtual idesyde::headers::ExplorationBid bid(shared_ptr<DecisionModel> m);
        virtual vector<DecisionModel> explore(
            shared_ptr<DecisionModel> m,
            uint64_t max_sols,
            uint64_t total_timeout,
            uint64_t time_resolution,
            uint64_t memory_resolution);
    };

}
#endif // CORE_H
