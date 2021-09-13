package idesyde.identification.models

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.{GenericDigitalInterconnect, GenericProcessingModule, RoundRobinScheduler, TimeTriggeredScheduler}
import idesyde.identification.DecisionModel

final case class SchedulableNetworkedDigHW (
    val hardware: NetworkedDigitalHardware,
    val timeTriggeredPEs: Set[GenericProcessingModule],
    val roundRobinPEs: Set[GenericProcessingModule],
    // val bandWidthFromCEtoPE: Map[GenericDigitalInterconnect, GenericProcessingModule],
    val schedulersFromPEs: Map[GenericProcessingModule, TimeTriggeredScheduler | RoundRobinScheduler]
                                     ) extends DecisionModel {

  val coveredVertexes: Iterable[Vertex] = hardware.coveredVertexes ++
    schedulersFromPEs.values.map(_.getViewedVertex).toSet
}
