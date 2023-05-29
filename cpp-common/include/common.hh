#if !defined(H_COMMON)
#define H_COMMON

#include <core.hh>

namespace idesyde::common
{

    /** Decision model for synchronous dataflow graphs.
     *
     * This decision model encodes a synchronous dataflow graphs without its explicit topology matrix,
     * also known as balance matrix in some newer texts. This is achieved by encoding the graph as (A
     * \cup C, E) where A is the set of actors, `actorsIdentifiers`, and $C$ is the set of channels,
     * `channelsIdentifiers`. Every edge in $E$ connects an actor to a channel or a channel to an
     * actor, i.e. $e \in E$ means that $e \in A \times C$ or $e \in C \times A$. These edges are
     * encoded with `topologySrcs`, `topologyDsts` and `topologyEdgeValue` for the amount of tokens
     * produced or consumed. For example, if $e = (a, c, 2)$, then the edge $e$ is the production of 2
     * tokens from the actor $a$ to channel $c$. The other parameters bring enough instrumentation
     * information so that the decision model can potentially be mapped into a target platform.
     *
     * @param actorsIdentifiers
     *   the set of actors
     * @param channelsIdentifiers
     *   the set of channels
     * @param topologySrcs
     *   the sources for every edge triple in the SDF graph.
     * @param topologyDsts
     *   the target for every edge triple in the SDF graph.
     * @param topologyEdgeValue
     *   the produced or consumed tokens for each edge triple in the SDF graph.
     * @param actorSizes
     *   the size in bits for each actor's instruction(s)
     * @param minimumActorThroughputs
     *   the fixed throughput expected to be done for each actor, given in executions per second.
     *
     * @see
     *   [[InstrumentedWorkloadMixin]] for descriptions of the computational and memory needs.
     */
    class SDFApplication : idesyde::core::DecisionModel
    {
    private:
        /* data */
    public:
        std::vector<string> actorsIdentifiers;
        std::vector<string> channelsIdentifiers;
        std::vector<string> topologySrcs;
        std::vector<string> topologyDsts;
        std::vector<int> topologyEdgeValue;
        std::map<string, long> actorSizes;
        std::map<string, std::map<string, std::map<string, long>>> actorComputationalNeeds;
        std::map<string, int> channelNumInitialTokens;
        std::map<string, long> channelTokenSizes;
        std::map<string, double> minimumActorThroughputs;
        SDFApplication(
            std::vector<string> actorsIdentifiers,
            std::vector<string> channelsIdentifiers,
            std::vector<string> topologySrcs,
            std::vector<string> topologyDsts,
            std::vector<int> topologyEdgeValue,
            std::map<string, long> actorSizes,
            std::map<string, std::map<string, std::map<string, long>>> actorComputationalNeeds,
            std::map<string, int> channelNumInitialTokens,
            std::map<string, long> channelTokenSizes,
            std::map<string, double> minimumActorThroughputs);
        ~SDFApplication();
    };

    SDFApplication::SDFApplication(
        std::vector<string> actorsIdentifiers,
        std::vector<string> channelsIdentifiers,
        std::vector<string> topologySrcs,
        std::vector<string> topologyDsts,
        std::vector<int> topologyEdgeValue,
        std::map<string, long> actorSizes,
        std::map<string, std::map<string, std::map<string, long>>> actorComputationalNeeds,
        std::map<string, int> channelNumInitialTokens,
        std::map<string, long> channelTokenSizes,
        std::map<string, double> minimumActorThroughputs)
    {
        this->actorsIdentifiers = actorsIdentifiers;
        this->channelsIdentifiers = channelsIdentifiers;
        this->topologySrcs = topologySrcs;
        this->topologyDsts = topologyDsts;
        this->topologyEdgeValue = topologyEdgeValue;
        this->actorSizes = actorSizes;
        this->actorComputationalNeeds = actorComputationalNeeds;
        this->channelNumInitialTokens = channelNumInitialTokens;
        this->channelTokenSizes = channelTokenSizes;
        this->minimumActorThroughputs = minimumActorThroughputs;
    }

    SDFApplication::~SDFApplication()
    {
    }

    class TiledMultiCore
    {
    private:
        /* data */
    public:
        std::vector<string> processors;
        std::vector<string> memories;
        std::vector<string> networkInterfaces;
        std::vector<string> routers;
        std::vector<string> interconnectTopologySrcs;
        std::vector<string> interconnectTopologyDsts;
        std::map<string, long> processorsFrequency;
        std::map<string, long> tileMemorySizes;
        std::map<string, int> communicationElementsMaxChannels;
        std::map<string, double> communicationElementsBitPerSecPerChannel;
        std::map<string, std::map<string, std::vector<string>>> preComputedPaths;
        TiledMultiCore(/* args */);
        ~TiledMultiCore();
    };

    TiledMultiCore::TiledMultiCore(/* args */)
    {
    }

    TiledMultiCore::~TiledMultiCore()
    {
    }

    class SDFToTiledMulticore : idesyde::core::DecisionModel
    {
    private:
        /* data */
    public:
        std::shared_ptr<SDFApplication> sdfApplication;
        SDFToTiledMulticore(std::shared_ptr<SDFApplication> sdfApplication);
        ~SDFToTiledMulticore();
    };

    SDFToTiledMulticore::SDFToTiledMulticore(std::shared_ptr<SDFApplication> sdfApplication)
    {
        this->sdfApplication = sdfApplication;
    }

    SDFToTiledMulticore::~SDFToTiledMulticore()
    {
    }

} // namespace common

#endif // H_COMMON
