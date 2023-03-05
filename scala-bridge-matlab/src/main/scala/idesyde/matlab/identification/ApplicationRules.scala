package idesyde.matlab.identification

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.matlab.utils.MatlabUtils

trait ApplicationRules extends MatlabUtils {

  def identCommunicatingAndTriggeredReactiveWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[CommunicatingAndTriggeredReactiveWorkload] = toSimulinkReactiveDesignModel(models) {
    model =>
      Set(
        CommunicatingAndTriggeredReactiveWorkload(
          model.processes ++ model.delays,
          model.processesSizes ++ model.delaysSizes,
          model.processesOperations ++ model.delaysOperations,
          model.linksSrcs
            .zip(model.linksDsts)
            .map((s, t) => s + "--" + t) ++ model.sources ++ model.sinks,
          model.linksDataSizes ++ model.sourcesSizes ++ model.sinksSizes,
          model.linksSrcs
            .zip(model.linksDsts)
            .zip(model.linksDataSizes)
            .map((st, l) => (st._1, st._2, l))
            .toSet,
          model.sources,
          model.sourcesPeriodsNumerator,
          model.sourcesPeriodsDenominator,
          Vector.fill(model.sources.size)(0L),
          Vector.fill(model.sources.size)(1L),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          model.linksSrcs
            .zip(model.linksDsts),
          Set.empty
        )
      )
  }
}
