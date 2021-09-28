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
          hardware.paths.getOrElse((mm, pp), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum +
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
          hardware.paths.getOrElse((mm, pp), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val roundRobinTopologySymmetryRelation: Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- roundRobinPEs;
      pp <- roundRobinPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths.getOrElse((mm, pp), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val topologicallySymmetricGroups: Set[Set[GenericProcessingModule]] =
    fixedPriorityPEs.map(p =>
      fixedPriorityPEs.filter(pp => fixedPriorityTopologySymmetryRelation.contains(p, pp))
    ) ++
      timeTriggeredPEs.map(p =>
        timeTriggeredPEs.filter(pp => timeTriggeredTopologySymmetryRelation.contains(p, pp))
      ) ++
      roundRobinPEs.map(p =>
        roundRobinPEs.filter(pp => roundRobinTopologySymmetryRelation.contains(p, pp))
      )

  override val uniqueIdentifier = "SchedulableNetworkedDigHW"
}
