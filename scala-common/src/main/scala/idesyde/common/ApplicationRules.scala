package idesyde.common

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger

trait ApplicationRules {
  def identCommonSDFApplication(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SDFApplication], Set[String]) = {
    (
      identified
        .flatMap(_ match {
          case m: SDFApplicationWithFunctions => Some(m)
          case _                              => None
        })
        .map(sdfWithFunctions => {
          val actors   = sdfWithFunctions.actorsIdentifiers.toSet
          val channels = sdfWithFunctions.channelsIdentifiers.toSet
          SDFApplication(
            actors_identifiers = actors,
            channels_identifiers = channels,
            self_concurrent_actors = actors.filter(sdfWithFunctions.isSelfConcurrent),
            actor_minimum_throughputs = actors
              .map(a =>
                a -> sdfWithFunctions
                  .minimumActorThroughputs(sdfWithFunctions.actorsIdentifiers.indexOf(a))
              )
              .toMap,
            topology_dsts =
              sdfWithFunctions.sdfMessages.map((src, dst, channel, msize, prod, cons, toks) => dst),
            topology_production = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => prod),
            topology_srcs =
              sdfWithFunctions.sdfMessages.map((src, dst, channel, msize, prod, cons, toks) => src),
            topology_consumption = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => cons),
            topology_initial_token = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => toks),
            topology_channel_names = sdfWithFunctions.sdfMessages
              .map((src, dst, channels, msize, prod, cons, toks) => channels),
            chain_maximum_latency = Map()
          )
        }),
      Set()
    )
  }
}
