package idesyde.forsydeio

import scala.jdk.CollectionConverters._

import idesyde.forsydeio.ForSyDeIdentificationUtils
import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.common.AperiodicAsynchronousDataflow
import scala.collection.mutable
import forsyde.io.lib.hierarchy.ForSyDeHierarchy
import forsyde.io.lib.hierarchy.behavior.moc.sy.SYMap
import forsyde.io.lib.hierarchy.behavior.moc.sy.SYSignal
import forsyde.io.lib.hierarchy.behavior.moc.sy.SYDelay
import org.jgrapht.graph.AsSubgraph
import java.util.stream.Collectors
import org.jgrapht.alg.connectivity.ConnectivityInspector

trait ApplicationRules {

  def identAperiodicDataflowFromSY(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[AperiodicAsynchronousDataflow], Set[String]) = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var identified = mutable.Set[AperiodicAsynchronousDataflow]()
      var msgs       = mutable.Set[String]()
      val onlySyComponents = AsSubgraph(
        model,
        model
          .vertexSet()
          .stream()
          .filter(v =>
            ForSyDeHierarchy.SYProcess.tryView(model, v).isPresent() || ForSyDeHierarchy.SYSignal
              .tryView(model, v)
              .isPresent()
          )
          .collect(Collectors.toSet())
      )
      val inspector = ConnectivityInspector(onlySyComponents)
      val wcc       = inspector.connectedSets()
      if (wcc.isEmpty()) msgs += "identAperiodicDataflowFromSY: not SY network found"
      wcc
        .stream()
        .forEach(subModel => {
          var syMaps    = mutable.Set[SYMap]()
          var sySignals = mutable.Set[SYSignal]()
          var syDelays  = mutable.Set[SYDelay]()
          subModel
            .forEach(v => {
              ForSyDeHierarchy.SYMap.tryView(model, v).ifPresent(syMaps.add)
              ForSyDeHierarchy.SYSignal.tryView(model, v).ifPresent(sySignals.add)
              ForSyDeHierarchy.SYDelay.tryView(model, v).ifPresent(syDelays.add)
            })
          val msgSizes = sySignals
            .map(sig =>
              sig.getIdentifier() -> ForSyDeHierarchy.RegisterArrayLike
                .tryView(sig)
                .map(_.elementSizeInBits().toLong)
                .orElse(0L)
            )
            .toMap
          val mapsAndDelays = syMaps ++ syDelays
          val jobGraph = sySignals
            .flatMap(sig => {
              sig
                .consumers()
                .asScala
                .flatMap(dst => {
                  val src = sig.producer()
                  if (src != null) {
                    if (
                      ForSyDeHierarchy.SYMap
                        .tryView(src)
                        .isPresent() && ForSyDeHierarchy.SYMap.tryView(dst).isPresent()
                    ) {
                      Some((src, dst, true))
                    } else if (ForSyDeHierarchy.SYSignal.tryView(src).isPresent()) {
                      Some((dst, src, true))
                    } else {
                      None
                    }
                  } else None
                })
            })
            .toVector
          identified += AperiodicAsynchronousDataflow(
            buffer_max_size_in_bits = msgSizes,
            buffers = sySignals.map(_.getIdentifier()).toSet,
            job_graph_dst_instance = jobGraph.map((s, t, b) => 1),
            job_graph_dst_name = jobGraph.map((s, t, b) => s.getIdentifier()),
            job_graph_is_strong_precedence = jobGraph.map((s, t, b) => b),
            job_graph_src_instance = jobGraph.map((s, t, b) => 1),
            job_graph_src_name = jobGraph.map((s, t, b) => t.getIdentifier()),
            process_get_from_buffer_in_bits = mapsAndDelays
              .map(proc =>
                proc.getIdentifier() -> sySignals
                  .filter(sig => sig.consumers().contains(proc))
                  .map(sig =>
                    sig.getIdentifier() ->
                      msgSizes(sig.getIdentifier())
                  )
                  .toMap
              )
              .toMap,
            process_minimum_throughput = Map(),
            process_path_maximum_latency = Map(),
            process_put_in_buffer_in_bits = mapsAndDelays
              .map(proc =>
                proc.getIdentifier() -> sySignals
                  .filter(sig => sig.producer() == proc)
                  .map(sig =>
                    sig.getIdentifier() ->
                      msgSizes(sig.getIdentifier())
                  )
                  .toMap
              )
              .toMap,
            processes = syMaps.map(_.getIdentifier()).toSet ++ syDelays.map(_.getIdentifier()).toSet
          )
        })
      (identified.toSet, msgs.toSet)
    }
  }
}
