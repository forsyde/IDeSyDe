#if !defined(CORE_H)
#define CORE_H

#include <nlohmann/json.hpp>

#include <optional>

namespace core
{

#include <headers.hh>

    virtual class DecisionModel
    {
    public:
        virtual DecisionModelHeader header();

        std::optional<nlohmann::json> body_as_json()
        {
            return std::nullopt;
        };

        std::optional<uint8_t[]> body_as_cbor()
        {
            return std::nullopt;
        };
    };

    virtual class DesignModel
    {
    public:
        virtual DesignModelHeader header();
    };

}
#endif // CORE_H
