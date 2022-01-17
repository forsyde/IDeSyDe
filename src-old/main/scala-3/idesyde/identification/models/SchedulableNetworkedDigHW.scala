package idesyde.identification.models

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.{
  GenericDigitalInterconnect,
  GenericProcessingModule,
  RoundRobinScheduler,
  TimeTriggeredScheduler
}
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.FixedPriorityScheduler
import scala.annotation.tailrec
import org.jgrapht.graph.SimpleGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector

import collection.JavaConverters.*
import java.util.stream.Collectors
import org.jgrapht.alg.connectivity.ConnectivityInspector

final case class SchedulableNetworkedDigHW(
    val hardware: NetworkedDigitalHardware,
    val fixedPriorityPEs: Set[GenericProcessingModule],
    val timeTriggeredPEs: Set[GenericProcessingModule],
    val roundRobinPEs: Set[GenericProcessingModule],
    // val bandWidthFromCEtoPE: Map[GenericDigitalInterconnect, GenericProcessingModule],
    val schedulersFromPEs: Map[
      GenericProcessingModule,
      FixedPriorityScheduler | TimeTriggeredScheduler | RoundRobinScheduler
    ]
) extends DecisionModel {

  val coveredVertexes: Iterable[Vertex] = hardware.coveredVertexes ++
    schedulersFromPEs.values.map(_.getViewedVertex).toSet

  lazy val fixedPriorityTopologySymmetryRelation
      : Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- fixedPriorityPEs;
      pp <- fixedPriorityPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths
            .getOrElse((mm, pp), Seq())
            .map(c => hardware.bandWidthBitPerSec(c, pp))
            .sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val timeTriggeredTopologySymmetryRelation
      : Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- timeTriggeredPEs;
      pp <- timeTriggeredPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths
            .getOrElse((mm, pp), Seq())
            .map(c => hardware.bandWidthBitPerSec(c, pp))
            .sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val roundRobinTopologySymmetryRelation
      : Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- roundRobinPEs;
      pp <- roundRobinPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths
            .getOrElse((mm, pp), Seq())
            .map(c => hardware.bandWidthBitPerSec(c, pp))
            .sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val topologySymmetryRelationGraph: SimpleGraph[GenericProcessingModule, DefaultEdge] =
    val graph = SimpleGraph[GenericProcessingModule, DefaultEdge](() => DefaultEdge())
    hardware.processingElems.foreach(p => graph.addVertex(p))
    for (
      p  <- hardware.processingElems;
      pp <- hardware.processingElems - p;
      // TODO: this check should be a bit more robust... is it always master to slave?
      // can it be in any direction? After this design decision, it becomes better.
      // Currently we assume master to slace
      // TODO: the fact that the bandwith must get with a default value is not safe,
      // this should be removed later
      if hardware.storageElems.forall(m =>
        hardware.storageElems.exists(mm => {
          hardware.paths.contains((p, m)) &&
          hardware.paths.contains((pp, mm)) &&
          hardware.paths((p, m)).map(c => 1.0 / hardware.bandWidthBitPerSec((c, p))).sum ==
            hardware.paths((pp, mm)).map(c => 1.0 / hardware.bandWidthBitPerSec((c, pp))).sum
        })
      )
    ) graph.addEdge(p, pp)
    graph

  lazy val topologicallySymmetricGroups: Set[Set[GenericProcessingModule]] =
    ConnectivityInspector(topologySymmetryRelationGraph).connectedSets.stream
      .map(g => g.asScala.toSet)
      .collect(Collectors.toSet)
      .asScala
      .toSet

  override val uniqueIdentifier = "SchedulableNetworkedDigHW"
}
