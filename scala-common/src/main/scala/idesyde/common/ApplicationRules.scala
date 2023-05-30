package idesyde.common

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger

trait ApplicationRules {
  def identSDFApplication(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[SDFApplication] = {
    identified
      .flatMap(_ match {
        case m: SDFApplicationWithFunctions => Some(m)
        case _                              => None
      })
      .map(sdfWithFunctions => {
        val actors   = sdfWithFunctions.actorsIdentifiers.toSet
        val channels = sdfWithFunctions.channelsIdentifiers.toSet
        SDFApplication(
          actor_computational_needs = actors
            .map(a =>
              a -> sdfWithFunctions
                .actorComputationalNeeds(sdfWithFunctions.actorsIdentifiers.indexOf(a))
            )
            .toMap,
          actor_sizes = actors
            .map(a =>
              a -> sdfWithFunctions.actorSizes(sdfWithFunctions.actorsIdentifiers.indexOf(a))
            )
            .toMap,
          actors_identifiers = actors,
          channel_num_initial_tokens = channels
            .map(c =>
              c -> sdfWithFunctions
                .channelNumInitialTokens(sdfWithFunctions.channelsIdentifiers.indexOf(c))
                .toLong
            )
            .toMap,
          channel_token_sizes = channels
            .map(c =>
              c -> sdfWithFunctions
                .channelTokenSizes(sdfWithFunctions.channelsIdentifiers.indexOf(c))
            )
            .toMap,
          channels_identifiers = channels,
          minimum_actor_throughputs = actors
            .map(a =>
              a -> sdfWithFunctions
                .minimumActorThroughputs(sdfWithFunctions.actorsIdentifiers.indexOf(a))
            )
            .toMap,
          repetition_vector = actors
            .map(a =>
              a -> sdfWithFunctions
                .sdfRepetitionVectors(sdfWithFunctions.actorsIdentifiers.indexOf(a))
                .toLong
            )
            .toMap,
          topological_and_heavy_job_ordering =
            sdfWithFunctions.topologicalAndHeavyJobOrdering.map((a, q) => a).toVector,
          topology_dsts = sdfWithFunctions.topologyDsts,
          topology_edge_value = sdfWithFunctions.topologyEdgeValue.map(_.toLong),
          topology_srcs = sdfWithFunctions.topologySrcs
        )
      })
    Set()
  }
}
