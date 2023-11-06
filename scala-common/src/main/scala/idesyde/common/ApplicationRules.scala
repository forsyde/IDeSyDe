package idesyde.common

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.common.AnalysedSDFApplication

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
            channel_token_sizes = sdfWithFunctions.channelTokenSizes.zipWithIndex
              .map((ms, i) => sdfWithFunctions.channelsIdentifiers(i) -> ms)
              .toMap,
            topology_dsts =
              sdfWithFunctions.sdfMessages.map((src, dst, channel, msize, prod, cons, toks) => dst),
            topology_production = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => prod),
            topology_srcs =
              sdfWithFunctions.sdfMessages.map((src, dst, channel, msize, prod, cons, toks) => src),
            topology_consumption = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => cons),
            topology_initial_tokens = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => toks),
            topology_token_size_in_bits = sdfWithFunctions.sdfMessages
              .map((src, dst, channel, msize, prod, cons, toks) => msize),
            topology_channel_names = sdfWithFunctions.sdfMessages
              .map((src, dst, channels, msize, prod, cons, toks) => channels),
            chain_maximum_latency = Map()
          )
        }),
      Set()
    )
  }

  def identAnalysedSDFApplication(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[AnalysedSDFApplication], Set[String]) = {
    identified
      .flatMap(_ match {
        case m: SDFApplicationWithFunctions => Some(m)
        case _                              => None
      })
      .flatMap(sdfWithFunctions =>
        identified.flatMap(_ match {
          case m: SDFApplication =>
            if (m.actors_identifiers == sdfWithFunctions.actorsIdentifiers.toSet) {
              Some(sdfWithFunctions, m)
            } else None
          case _ => None
        })
      )
      .map((sdfWithFunctions, m) => {
        if (sdfWithFunctions.isConsistent) {
          (
            Option(
              AnalysedSDFApplication(
                sdfWithFunctions.topologicalAndHeavyJobOrdering.map((a, q) => a),
                sdfWithFunctions.actorsIdentifiers
                  .zip(sdfWithFunctions.sdfRepetitionVectors.map(_.toLong))
                  .toMap,
                m
              )
            ),
            None
          )
        } else {
          (None, Option("identAnalyzedSDFApplication: SDF is not consistent"))
        }
      })
      .foldLeft((Set(), Set()))((a, b) =>
        (b._1.map(a._1 + _).getOrElse(a._1), b._2.map(a._2 + _).getOrElse(a._2))
      )
  }

  // def identAperiodicAsynchronousDataflow(
  //     models: Set[DesignModel],
  //     identified: Set[DecisionModel]
  // ): (Set[SDFApplication], Set[String]) = {
  //   (
  //     identified
  //       .flatMap(_ match {
  //         case m: SDFApplicationWithFunctions => Some(m)
  //         case _                              => None
  //       })
  //       .map(sdfWithFunctions => {
  //         val actors   = sdfWithFunctions.actorsIdentifiers.toSet
  //         val channels = sdfWithFunctions.channelsIdentifiers.toSet
  //         val jobGraphPairs =
  //           sdfWithFunctions.firingsPrecedenceGraph.edges
  //             .map(e => (e.source.value._1, e.target.value._1))
  //         AperiodicAsynchronousDataflow(
  //           processes = actors,
  //           buffer_max_sizes =
  //             sdfWithFunctions.channelsIdentifiers.zip(sdfWithFunctions.messagesMaxSizes).toMap,
  //           jobs_of_processes = sdfWithFunctions.jobsAndActors.map((a, _) => a),
  //           job_graph_buffer_name = jobGraphPairs
  //             .flatMap(pair =>
  //               sdfWithFunctions.sdfMessages
  //                 .filter((src, dst, cs, m, prod, cons, tok) => pair == (src, dst))
  //                 .map((src, dst, cs, m, prod, cons, tok) => cs.toSet)
  //             )
  //             .toVector,
  //           job_graph_data_read = jobGraphPairs
  //             .flatMap(pair =>
  //               sdfWithFunctions.sdfMessages
  //                 .filter((src, dst, cs, m, prod, cons, tok) => pair == (src, dst))
  //                 .map((src, dst, cs, m, prod, cons, tok) => cons.toLong)
  //             )
  //             .toVector,
  //           job_graph_data_sent = jobGraphPairs
  //             .flatMap(pair =>
  //               sdfWithFunctions.sdfMessages
  //                 .filter((src, dst, cs, m, prod, cons, tok) => pair == (src, dst))
  //                 .map((src, dst, cs, m, prod, cons, tok) => prod.toLong)
  //             )
  //             .toVector,
  //           job_graph_src = jobGraphPairs
  //             .flatMap(pair =>
  //               sdfWithFunctions.sdfMessages
  //                 .filter((src, dst, cs, m, prod, cons, tok) => pair == (src, dst))
  //                 .map((src, dst, cs, m, prod, cons, tok) => actors.)
  //             )
  //             .toVector,
  //           job_graph_dst = jobGraphPairs
  //             .flatMap(pair =>
  //               sdfWithFunctions.sdfMessages
  //                 .filter((src, dst, cs, m, prod, cons, tok) => pair == (src, dst))
  //                 .map((src, dst, cs, m, prod, cons, tok) => dst)
  //             )
  //             .toVector,
  //           process_minimum_throughput = ???,
  //           process_path_maximum_latency = ???
  //         )
  //         SDFApplication(
  //           actors_identifiers = actors,
  //           channels_identifiers = channels,
  //           self_concurrent_actors = actors.filter(sdfWithFunctions.isSelfConcurrent),
  //           actor_minimum_throughputs = actors
  //             .map(a =>
  //               a -> sdfWithFunctions
  //                 .minimumActorThroughputs(sdfWithFunctions.actorsIdentifiers.indexOf(a))
  //             )
  //             .toMap,
  //           topology_dsts =
  //             sdfWithFunctions.sdfMessages.map((src, dst, channel, msize, prod, cons, toks) => dst),
  //           topology_production = sdfWithFunctions.sdfMessages
  //             .map((src, dst, channel, msize, prod, cons, toks) => prod),
  //           topology_srcs =
  //             sdfWithFunctions.sdfMessages.map((src, dst, channel, msize, prod, cons, toks) => src),
  //           topology_consumption = sdfWithFunctions.sdfMessages
  //             .map((src, dst, channel, msize, prod, cons, toks) => cons),
  //           topology_initial_token = sdfWithFunctions.sdfMessages
  //             .map((src, dst, channel, msize, prod, cons, toks) => toks),
  //           topology_channel_names = sdfWithFunctions.sdfMessages
  //             .map((src, dst, channels, msize, prod, cons, toks) => channels),
  //           chain_maximum_latency = Map()
  //         )
  //       }),
  //     Set()
  //   )
  // }
}
