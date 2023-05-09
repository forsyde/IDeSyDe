#if !defined(CORE_HEADERS_H)
#define CORE_HEADERS_H

#include <optional>
#include <stdexcept>
#include <regex>

// generated with the help of quicktype.io
#include <nlohmann/json.hpp>

#ifndef NLOHMANN_OPT_HELPER
#define NLOHMANN_OPT_HELPER
namespace nlohmann
{
    template <typename T>
    struct adl_serializer<std::shared_ptr<T>>
    {
        static void to_json(json &j, const std::shared_ptr<T> &opt)
        {
            if (!opt)
                j = nullptr;
            else
                j = *opt;
        }

        static std::shared_ptr<T> from_json(const json &j)
        {
            if (j.is_null())
                return std::make_shared<T>();
            else
                return std::make_shared<T>(j.get<T>());
        }
    };
    template <typename T>
    struct adl_serializer<std::optional<T>>
    {
        static void to_json(json &j, const std::optional<T> &opt)
        {
            if (!opt)
                j = nullptr;
            else
                j = *opt;
        }

        static std::optional<T> from_json(const json &j)
        {
            if (j.is_null())
                return std::make_optional<T>();
            else
                return std::make_optional<T>(j.get<T>());
        }
    };
}
#endif

namespace idesyde::headers
{
    using json = nlohmann::json;

#ifndef NLOHMANN_UNTYPED_headers_HELPER
#define NLOHMANN_UNTYPED_headers_HELPER
    inline json get_untyped(const json &j, const char *property)
    {
        if (j.find(property) != j.end())
        {
            return j.at(property).get<json>();
        }
        return json();
    }

    inline json get_untyped(const json &j, std::string property)
    {
        return get_untyped(j, property.data());
    }
#endif

#ifndef NLOHMANN_OPTIONAL_headers_HELPER
#define NLOHMANN_OPTIONAL_headers_HELPER
    template <typename T>
    inline std::shared_ptr<T> get_heap_optional(const json &j, const char *property)
    {
        auto it = j.find(property);
        if (it != j.end() && !it->is_null())
        {
            return j.at(property).get<std::shared_ptr<T>>();
        }
        return std::shared_ptr<T>();
    }

    template <typename T>
    inline std::shared_ptr<T> get_heap_optional(const json &j, std::string property)
    {
        return get_heap_optional<T>(j, property.data());
    }
    template <typename T>
    inline std::optional<T> get_stack_optional(const json &j, const char *property)
    {
        auto it = j.find(property);
        if (it != j.end() && !it->is_null())
        {
            return j.at(property).get<std::optional<T>>();
        }
        return std::optional<T>();
    }

    template <typename T>
    inline std::optional<T> get_stack_optional(const json &j, std::string property)
    {
        return get_stack_optional<T>(j, property.data());
    }
#endif

    class LabelledArcWithPorts
    {
    public:
        LabelledArcWithPorts() = default;
        virtual ~LabelledArcWithPorts() = default;

    private:
        std::string dst;
        std::optional<std::string> dst_port;
        std::optional<std::string> label;
        std::string src;
        std::optional<std::string> src_port;

    public:
        const std::string &get_dst() const { return dst; }
        std::string &get_mutable_dst() { return dst; }
        void set_dst(const std::string &value) { this->dst = value; }

        std::optional<std::string> get_dst_port() const { return dst_port; }
        void set_dst_port(std::optional<std::string> value) { this->dst_port = value; }

        std::optional<std::string> get_label() const { return label; }
        void set_label(std::optional<std::string> value) { this->label = value; }

        const std::string &get_src() const { return src; }
        std::string &get_mutable_src() { return src; }
        void set_src(const std::string &value) { this->src = value; }

        std::optional<std::string> get_src_port() const { return src_port; }
        void set_src_port(std::optional<std::string> value) { this->src_port = value; }
    };

    class DecisionModelHeader
    {
    public:
        DecisionModelHeader() = default;
        virtual ~DecisionModelHeader() = default;

    private:
        std::optional<std::string> body_path;
        std::string category;
        std::vector<std::string> covered_elements;
        std::vector<LabelledArcWithPorts> covered_relations;

    public:
        std::optional<std::string> get_body_path() const { return body_path; }
        void set_body_path(std::optional<std::string> value) { this->body_path = value; }

        const std::string &get_category() const { return category; }
        std::string &get_mutable_category() { return category; }
        void set_category(const std::string &value) { this->category = value; }

        const std::vector<std::string> &get_covered_elements() const { return covered_elements; }
        std::vector<std::string> &get_mutable_covered_elements() { return covered_elements; }
        void set_covered_elements(const std::vector<std::string> &value) { this->covered_elements = value; }

        const std::vector<LabelledArcWithPorts> &get_covered_relations() const { return covered_relations; }
        std::vector<LabelledArcWithPorts> &get_mutable_covered_relations() { return covered_relations; }
        void set_covered_relations(const std::vector<LabelledArcWithPorts> &value) { this->covered_relations = value; }
    };

    class DesignModelHeader
    {
    public:
        DesignModelHeader() = default;
        virtual ~DesignModelHeader() = default;

    private:
        std::string category;
        std::vector<std::string> elements;
        std::vector<std::string> model_paths;
        std::vector<LabelledArcWithPorts> relations;

    public:
        const std::string &get_category() const { return category; }
        std::string &get_mutable_category() { return category; }
        void set_category(const std::string &value) { this->category = value; }

        const std::vector<std::string> &get_elements() const { return elements; }
        std::vector<std::string> &get_mutable_elements() { return elements; }
        void set_elements(const std::vector<std::string> &value) { this->elements = value; }

        const std::vector<std::string> &get_model_paths() const { return model_paths; }
        std::vector<std::string> &get_mutable_model_paths() { return model_paths; }
        void set_model_paths(const std::vector<std::string> &value) { this->model_paths = value; }

        const std::vector<LabelledArcWithPorts> &get_relations() const { return relations; }
        std::vector<LabelledArcWithPorts> &get_mutable_relations() { return relations; }
        void set_relations(const std::vector<LabelledArcWithPorts> &value) { this->relations = value; }
    };

    class ExplorationBid
    {
    public:
        ExplorationBid() = default;
        virtual ~ExplorationBid() = default;

    private:
        bool can_explore;
        std::map<std::string, float> properties;

    public:
        bool get_can_explore() const { return can_explore; }
        void set_can_explore(const bool &value) { this->can_explore = value; }

        const std::map<std::string, float> &get_properties() const { return properties; }
        std::map<std::string, float> &get_mutable_properties() { return properties; }
        void set_properties(const std::map<std::string, float> &value) { this->properties = value; }
    };

}

namespace idesyde::headers
{

    using json = nlohmann::json;

    void from_json(const json &j, LabelledArcWithPorts &x);
    void to_json(json &j, const LabelledArcWithPorts &x);

    void from_json(const json &j, DecisionModelHeader &x);
    void to_json(json &j, const DecisionModelHeader &x);

    void from_json(const json &j, DesignModelHeader &x);
    void to_json(json &j, const DesignModelHeader &x);

    void from_json(const json &j, ExplorationBid &x);
    void to_json(json &j, const ExplorationBid &x);

    inline void from_json(const json &j, LabelledArcWithPorts &x)
    {
        x.set_dst(j.at("dst").get<std::string>());
        x.set_dst_port(get_stack_optional<std::string>(j, "dst_port"));
        x.set_label(get_stack_optional<std::string>(j, "label"));
        x.set_src(j.at("src").get<std::string>());
        x.set_src_port(get_stack_optional<std::string>(j, "src_port"));
    }

    inline void to_json(json &j, const LabelledArcWithPorts &x)
    {
        j = json::object();
        j["dst"] = x.get_dst();
        j["dst_port"] = x.get_dst_port();
        j["label"] = x.get_label();
        j["src"] = x.get_src();
        j["src_port"] = x.get_src_port();
    }

    inline void from_json(const json &j, DecisionModelHeader &x)
    {
        x.set_body_path(get_stack_optional<std::string>(j, "body_path"));
        x.set_category(j.at("category").get<std::string>());
        x.set_covered_elements(j.at("covered_elements").get<std::vector<std::string>>());
        x.set_covered_relations(j.at("covered_relations").get<std::vector<LabelledArcWithPorts>>());
    }

    inline void to_json(json &j, const DecisionModelHeader &x)
    {
        j = json::object();
        j["body_path"] = x.get_body_path();
        j["category"] = x.get_category();
        j["covered_elements"] = x.get_covered_elements();
        j["covered_relations"] = x.get_covered_relations();
    }

    inline void from_json(const json &j, DesignModelHeader &x)
    {
        x.set_category(j.at("category").get<std::string>());
        x.set_elements(j.at("elements").get<std::vector<std::string>>());
        x.set_model_paths(j.at("model_paths").get<std::vector<std::string>>());
        x.set_relations(j.at("relations").get<std::vector<LabelledArcWithPorts>>());
    }

    inline void to_json(json &j, const DesignModelHeader &x)
    {
        j = json::object();
        j["category"] = x.get_category();
        j["elements"] = x.get_elements();
        j["model_paths"] = x.get_model_paths();
        j["relations"] = x.get_relations();
    }

    inline void from_json(const json &j, ExplorationBid &x)
    {
        x.set_can_explore(j.at("can_explore").get<bool>());
        x.set_properties(j.at("properties").get<std::map<std::string, float>>());
    }

    inline void to_json(json &j, const ExplorationBid &x)
    {
        j = json::object();
        j["can_explore"] = x.get_can_explore();
        j["properties"] = x.get_properties();
    }

}
#endif // CORE_HEADERS_H
