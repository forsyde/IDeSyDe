package idesyde.common

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger

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
            taskSizes = m1.taskSizes ++ m2.taskSizes,
            taskComputationalNeeds = m1.taskComputationalNeeds ++ m2.taskComputationalNeeds,
            dataChannels = m1.dataChannels ++ m2.dataChannels,
            dataChannelSizes = m1.dataChannelSizes ++ m2.dataChannelSizes,
            dataGraphSrc = m1.dataGraphSrc ++ m2.dataGraphSrc,
            dataGraphDst = m1.dataGraphDst ++ m2.dataGraphDst,
            dataGraphMessageSize = m1.dataGraphMessageSize ++ m2.dataGraphMessageSize,
            periodicSources = m1.periodicSources ++ m2.periodicSources,
            periodsNumerator = m1.periodsNumerator ++ m2.periodsNumerator,
            periodsDenominator = m1.periodsDenominator ++ m2.periodsDenominator,
            offsetsNumerator = m1.offsetsNumerator ++ m2.offsetsNumerator,
            offsetsDenominator = m1.offsetsDenominator ++ m2.offsetsDenominator,
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
