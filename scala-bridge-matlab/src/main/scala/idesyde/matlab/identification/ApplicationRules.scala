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
      val procs   = model.processes.toVector
      val delays  = model.delays.toVector
      val links   = model.links.toVector
      val sources = model.sources.toVector
      val sinks   = model.sinks.toVector
      Set(
        CommunicatingAndTriggeredReactiveWorkload(
          procs ++ delays,
          procs.map(model.processesSizes) ++ delays.map(model.delaysSizes),
          procs.map(model.processesOperations) ++ delays.map(model.delaysOperations),
          links.map((s, t, sp, tp, _) => s + ":" + sp + "--" + t + ":" + tp) ++ sources ++ sinks,
          links.map((s, t, sp, tp, d) => d) ++ sources.map(model.sourcesSizes) ++ sinks.map(
            model.sinksSizes
          ),
          links
            .groupBy((s, t, _, _, _) => (s, t))
            .map((st, pairs) =>
              (st._1, st._2, pairs.map(_._5).sum)
            ) // just summing the data transmissed from s to t in all links
            .toSet,
          sources,
          sources.map(model.sourcesPeriodsNumerator),
          sources.map(model.sourcesPeriodsDenominator),
          Vector.fill(model.sources.size)(0L),
          Vector.fill(model.sources.size)(1L),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          links
            .groupBy((s, t, _, _, _) => (s, t))
            .map((st, _) =>
              (st._1, st._2)
            ) // just summing the data transmissed from s to t in all links
            .toVector,
          Set.empty
        )
      )
  }
}
