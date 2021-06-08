package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex

// class SDFExecution(DecisionModel):
//     """
//     This decision model captures all SDF actors and channels in
//     the design model and can only be identified if the 'Global' SDF
//     application (the union of all disjoint SDFs) is consistent, i.e.
//     it has a PASS.

//     After identification this decision model provides the global
//     SDF topology and the PASS with all elements included.
//     """

//     sdf_actors: Sequence[Vertex] = field(default_factory=list)
//     # sdf_constructors: Mapping[Process, SDFComb] = field(default_factory=dict)
//     sdf_impl: Mapping[Vertex, Vertex] = field(default_factory=dict)
//     sdf_delays: Sequence[Vertex] = field(default_factory=list)
//     sdf_channels: Mapping[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]] = field(default_factory=dict)
//     sdf_topology: List[List[int]] = field(default_factory=list)
//     sdf_repetition_vector: List[int] = field(default_factory=list)
//     sdf_initial_tokens: List[int] = field(default_factory=list)
//     sdf_pass: Sequence[Vertex] = field(default_factory=list)

//     sdf_max_tokens: List[int] = field(default_factory=list)

//     def covered_vertexes(self):
//         yield from self.sdf_actors
//         for paths in self.sdf_channels.values():
//             for p in paths:
//                 yield from p
//         # yield from self.sdf_constructors.values()
//         yield from self.sdf_impl.values()

//     def compute_deduced_properties(self):
//         self.max_tokens = [0 for c in self.sdf_channels]
//         for (cidx, c) in enumerate(self.sdf_channels):
//             self.max_tokens[cidx] = max(
//                 self.sdf_topology[cidx][aidx] * self.sdf_repetition_vector[aidx]
//                 for (aidx, a) in enumerate(self.sdf_actors)
//             )

final case class SDFApplication(
    val actors: Seq[Vertex],
    val delays: Seq[Vertex],
    val channels: Map[(Int, Int), Seq[Seq[Vertex]]],
    val topology: Seq[Seq[Int]],
    val initialTokens: Seq[Int],
    val impl: Map[Vertex, Vertex],
    val repetitionVector: Seq[Int],
    val sdfPass: Seq[Vertex]
) extends DecisionModel {

  override def dominates(o: DecisionModel) = {
    val extra: Boolean = o match {
      case o: SDFApplication => dominates_sdf(o)
      case _                 => true
    }
    super.dominates(o) && extra
  }

  def dominates_sdf(other: SDFApplication) =
    repetitionVector.count(i => i > 0) >= other.repetitionVector.count(i =>
      i > 0
    ) &&
      sdfPass.size >= other.sdfPass.size

  def coveredVertexes() = {
    for (a <- actors) yield a
    for (d <- delays) yield d
    for ((_, paths) <- channels; path <- paths; elem <- path) yield elem
    for ((_, v) <- impl) yield v
  }

  def coveredEdges() = {
    // TODO: Needs to be properly implemented later
    Seq()
  }
}
