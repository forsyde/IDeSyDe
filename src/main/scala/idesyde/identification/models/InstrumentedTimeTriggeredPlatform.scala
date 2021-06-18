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

final case class InstrumentedTimeTriggeredPlatform(
    val identifiedPlatform: TimeTriggeredPlatform,
    val procElemsMaxMemory: Map[Vertex, Int],
    val commElemsMinBandwidth: Map[Vertex, Int],
    val procElemsMaxCyclesPerOp: Map[Vertex, Map[String, Int]]
) extends DecisionModel {

  def processingElems = identifiedPlatform.processingElems

  def communicationElems = identifiedPlatform.communicationElems

  def commBetweenProcs = identifiedPlatform.commBetweenProcs

  def coveredVertexes() = {
    for (pset <- processingElems; p <- pset) yield p
    for (cset <- communicationElems; c <- cset) yield c
  }

  def coveredEdges() = Seq()

  override def dominates(o: DecisionModel) = o match {
    case o: InstrumentedTimeTriggeredPlatform =>
      super.dominates(o) && dominatesTTPlatform(o)
    case _ => super.dominates(o)
  }

  def dominatesTTPlatform(o: InstrumentedTimeTriggeredPlatform) =
    procElemsMaxMemory.exists((k, v) =>
      v != 0 && o.procElemsMaxMemory.getOrElse(k, 0) != 0
    ) &&
      commElemsMinBandwidth.exists((k, v) =>
        v != 0 && o.commElemsMinBandwidth.getOrElse(k, 0) != 0
      ) &&
      procElemsMaxMemory.exists((k, v) =>
        v != 0 && o.procElemsMaxMemory.getOrElse(k, 0) != 0
      )

}
