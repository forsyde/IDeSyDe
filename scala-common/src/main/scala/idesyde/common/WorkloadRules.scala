package idesyde.common

import idesyde.core.DesignModel
import idesyde.core.DecisionModel

trait WorkloadRules {

  def identAggregatedCommunicatingAndTriggeredReactiveWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[CommunicatingAndTriggeredReactiveWorkload], Set[String]) =
    (
      identified
        .flatMap(_ match {
          case m: CommunicatingAndTriggeredReactiveWorkload => Some(m)
          case _                                            => None
        })
        .reduceOption((m1, m2) => {
          CommunicatingAndTriggeredReactiveWorkload(
            tasks = m1.tasks ++ m2.tasks,
            task_sizes = m1.task_sizes ++ m2.task_sizes,
            task_computational_needs = m1.task_computational_needs ++ m2.task_computational_needs,
            data_channels = m1.data_channels ++ m2.data_channels,
            data_channel_sizes = m1.data_channel_sizes ++ m2.data_channel_sizes,
            data_graph_src = m1.data_graph_src ++ m2.data_graph_src,
            data_graph_dst = m1.data_graph_dst ++ m2.data_graph_dst,
            data_graph_message_size = m1.data_graph_message_size ++ m2.data_graph_message_size,
            periodic_sources = m1.periodic_sources ++ m2.periodic_sources,
            periods_numerator = m1.periods_numerator ++ m2.periods_numerator,
            periods_denominator = m1.periods_denominator ++ m2.periods_denominator,
            offsets_numerator = m1.offsets_numerator ++ m2.offsets_numerator,
            offsets_denominator = m1.offsets_denominator ++ m2.offsets_denominator,
            upsamples = m1.upsamples ++ m2.upsamples,
            upsampleRepetitiveHolds = m1.upsampleRepetitiveHolds ++ m2.upsampleRepetitiveHolds,
            upsampleInitialHolds = m1.upsampleInitialHolds ++ m2.upsampleInitialHolds,
            downsamples = m1.downsamples ++ m2.downsamples,
            downampleRepetitiveSkips = m1.downampleRepetitiveSkips ++ m2.downampleRepetitiveSkips,
            downampleInitialSkips = m1.downampleInitialSkips ++ m2.downampleInitialSkips,
            triggerGraphSrc = m1.triggerGraphSrc ++ m2.triggerGraphSrc,
            triggerGraphDst = m1.triggerGraphDst ++ m2.triggerGraphDst,
            hasORTriggerSemantics = m1.hasORTriggerSemantics ++ m2.hasORTriggerSemantics
          )
        })
        .map(Set(_))
        .getOrElse(Set()),
      Set()
    )

}
