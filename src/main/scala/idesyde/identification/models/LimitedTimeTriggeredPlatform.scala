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

final case class LimitedTimeTriggeredPlatform(
    val processingElems: Seq[Set[Vertex]],
    val communicationElems: Seq[Set[Vertex]],
    val commBetweenProcs: Map[(Int, Int), Seq[Seq[Int]]],
    val procElemsMaxMemory: Seq[Int],
    val commElemsMinBandwidth: Seq[Int],
    val procElemsMaxCyclesPerOp: Seq[Map[String, Int]]
) extends DecisionModel {

  def coveredVertexes() = {
    for (pset <- processingElems; p <- pset) yield p
    for (cset <- communicationElems; c <- cset) yield c
  }

  def coveredEdges() = Seq()

  override def dominates(o: DecisionModel) = {
    val extra = o match {
      case o: LimitedTimeTriggeredPlatform => domiantes_equal(o)
      case _                               => true
    }
    super.dominates(o) && extra
  }

  def domiantes_equal(o: LimitedTimeTriggeredPlatform) =
    this.procElemsMaxMemory.count(i => i > 0) >= o.procElemsMaxMemory.count(i =>
      i > 0
    ) &&
      this.commElemsMinBandwidth.count(i => i > 0) >= o.commElemsMinBandwidth
        .count(i => i > 0)
}
