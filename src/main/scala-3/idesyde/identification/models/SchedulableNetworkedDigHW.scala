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

  override val uniqueIdentifier = "SchedulableNetworkedDigHW"
}
