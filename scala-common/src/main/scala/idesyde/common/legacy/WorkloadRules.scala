package idesyde.common.legacy

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.common.legacy.CommonModule.tryCast

trait WorkloadRules {

  def identAggregatedCommunicatingAndTriggeredReactiveWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[CommunicatingAndTriggeredReactiveWorkload], Set[String]) =
    tryCast(identified, classOf[CommunicatingAndTriggeredReactiveWorkload]) { filtered => 
      val proper = filtered.reduceOption((m1, m2) => {
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
            upsample_repetitive_holds = m1.upsample_repetitive_holds ++ m2.upsample_repetitive_holds,
            upsample_initial_holds = m1.upsample_initial_holds ++ m2.upsample_initial_holds,
            downsamples = m1.downsamples ++ m2.downsamples,
            downample_repetitive_skips = m1.downample_repetitive_skips ++ m2.downample_repetitive_skips,
            downample_initial_skips = m1.downample_initial_skips ++ m2.downample_initial_skips,
            trigger_graph_src = m1.trigger_graph_src ++ m2.trigger_graph_src,
            trigger_graph_dst = m1.trigger_graph_dst ++ m2.trigger_graph_dst,
            has_or_trigger_semantics = m1.has_or_trigger_semantics ++ m2.has_or_trigger_semantics
          )
        })
        .map(Set(_))
        .getOrElse(Set())
      (proper, Set())
    }

}
