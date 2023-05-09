#if !defined(CORE_H)
#define CORE_H

#include <nlohmann/json.hpp>

#include <optional>
#include <functional>

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
            for (auto &v : oh.get_covered_elements())
            {
                if (std::find(h.get_covered_elements().begin(), h.get_covered_elements().end(), v) == h.get_covered_elements().end())
                {
                    return false;
                }
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

}
#endif // CORE_H
