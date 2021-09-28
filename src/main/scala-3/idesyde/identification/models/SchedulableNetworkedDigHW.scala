package idesyde.identification.models

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.{GenericDigitalInterconnect, GenericProcessingModule, RoundRobinScheduler, TimeTriggeredScheduler}
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.FixedPriorityScheduler

final case class SchedulableNetworkedDigHW (
    val hardware: NetworkedDigitalHardware,
    val fixedPriorityPEs: Set[GenericProcessingModule],
    val timeTriggeredPEs: Set[GenericProcessingModule],
    val roundRobinPEs: Set[GenericProcessingModule],
    // val bandWidthFromCEtoPE: Map[GenericDigitalInterconnect, GenericProcessingModule],
    val schedulersFromPEs: Map[GenericProcessingModule, FixedPriorityScheduler | TimeTriggeredScheduler | RoundRobinScheduler]
                                     ) extends DecisionModel {

  val coveredVertexes: Iterable[Vertex] = hardware.coveredVertexes ++
    schedulersFromPEs.values.map(_.getViewedVertex).toSet

  lazy val fixedPriorityPESymmetryRelation: Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (p <- fixedPriorityPEs;
        pp <- fixedPriorityPEs;
        m <- hardware.storageElems
        // check if exists one AND ONLY ONE memory element in which the path bandwidths
        // are exactly equal.
        if hardware.storageElems.count(mm => {
          hardware.paths((m, p)).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths((p, m)).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths((mm, pp)).map(c => hardware.bandWidthBitPerSec(c, pp)).sum +
          hardware.paths((pp, mm)).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
        }) == 1) yield (p, pp)

  lazy val timeTriggeredPESymmetryRelation: Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (p <- timeTriggeredPEs;
        pp <- timeTriggeredPEs;
        m <- hardware.storageElems
        // check if exists one AND ONLY ONE memory element in which the path bandwidths
        // are exactly equal.
        if hardware.storageElems.count(mm => {
          hardware.paths((m, p)).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths((p, m)).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths((mm, pp)).map(c => hardware.bandWidthBitPerSec(c, pp)).sum +
          hardware.paths((pp, mm)).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
        }) == 1) yield (p, pp)

  lazy val roundRobinPESymmetryRelation: Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (p <- roundRobinPEs;
        pp <- roundRobinPEs;
        m <- hardware.storageElems
        // check if exists one AND ONLY ONE memory element in which the path bandwidths
        // are exactly equal.
        if hardware.storageElems.count(mm => {
          hardware.paths((m, p)).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths((p, m)).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths((mm, pp)).map(c => hardware.bandWidthBitPerSec(c, pp)).sum +
          hardware.paths((pp, mm)).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
        }) == 1) yield (p, pp)

  lazy val symmetricGroups: Set[Set[GenericProcessingModule]] =
    fixedPriorityPEs.map(pe => {
      
    })

  override val uniqueIdentifier = "SchedulableNetworkedDigHW"
}
