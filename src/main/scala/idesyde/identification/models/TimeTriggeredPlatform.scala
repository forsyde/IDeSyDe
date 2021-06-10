package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex

// class TimeTriggeredPlatform(DecisionModel):

//     schedulers: Sequence[Vertex] = field(default_factory=list)
//     cores: Sequence[Vertex] = field(default_factory=list)
//     comms: Sequence[Vertex] = field(default_factory=list)
//     core_scheduler: Mapping[Vertex, Vertex] = field(default_factory=dict)
//     comm_scheduler: Mapping[Vertex, Vertex] = field(default_factory=dict)
//     paths: Mapping[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]] = field(default_factory=dict)
//     core_memory: Mapping[Vertex, int] = field(default_factory=dict)
//     comms_bandwidth: Mapping[Vertex, int] = field(default_factory=dict)

//     abstracted_vertexes: Sequence[Vertex] = field(default_factory=list)

final case class TimeTriggeredPlatform(
    val processingElems: Set[Set[Vertex]],
    val communicationElems: Set[Set[Vertex]],
    val commBetweenProcs: Map[(Vertex, Vertex), Seq[Seq[Vertex]]]
) extends DecisionModel {

  def coveredVertexes() = {
    for (pset <- processingElems; p <- pset) yield p
    for (cset <- communicationElems; c <- cset) yield c
  }

  def coveredEdges() = Seq()
}