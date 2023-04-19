package idesyde.matlab.identification

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.matlab.utils.MatlabUtils
import spire.math.Rational

trait ApplicationRules extends MatlabUtils {

  def identCommunicatingAndTriggeredReactiveWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[CommunicatingAndTriggeredReactiveWorkload] = toSimulinkReactiveDesignModel(models) {
    model =>
      val procs  = model.processes.toVector
      val delays = model.delays.toVector
      val linksWithoutConstants = model.links
        .filterNot((s, t, _, _, _) => model.constants.contains(s) || model.constants.contains(t))
        .toVector
      val sources = model.sources.toVector
      val sinks   = model.sinks.toVector
      // val (sourcesPeriodsNumerator, sourcesPeriodsDenominator) =
      val (pernums, perdens) = model.sources.toVector
        .map(model.sourcesPeriods)
        .map(Rational(_))
        .map(r => (r.numeratorAsLong, r.denominatorAsLong))
        .unzip
      val groupedLinks = linksWithoutConstants.groupBy((s, t, _, _, _) => (s, t))
      Set(
        CommunicatingAndTriggeredReactiveWorkload(
          procs ++ delays,
          procs.map(model.processesSizes) ++ delays.map(model.delaysSizes),
          procs.map(model.processesOperations) ++ delays.map(model.delaysOperations),
          linksWithoutConstants.map((s, t, sp, tp, _) =>
            s + ":" + sp + "--" + t + ":" + tp
          ) ++ sources ++ sinks,
          linksWithoutConstants.map((s, t, sp, tp, d) => d) ++ sources.map(
            model.sourcesSizes
          ) ++ sinks.map(
            model.sinksSizes
          ),
          groupedLinks.keySet.toVector.map((s, t) => s),
          groupedLinks.keySet.toVector.map((s, t) => t),
          groupedLinks.keySet.toVector.map(st => groupedLinks(st).map((_, _, _, _, m) => m).sum),
          sources,
          pernums,
          perdens,
          Vector.fill(model.sources.size)(0L),
          Vector.fill(model.sources.size)(1L),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          groupedLinks.keySet.toVector.map((s, t) => s),
          groupedLinks.keySet.toVector.map((s, t) => t),
          Set.empty
        )
      )
  }
}
