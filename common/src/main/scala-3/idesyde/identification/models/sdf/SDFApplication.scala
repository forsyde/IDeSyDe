package idesyde.identification.models.sdf

import forsyde.io.java.core.Vertex
import idesyde.identification.DecisionModel

final case class SDFApplication(
    val actors: Set[Vertex],
    val delays: Set[Vertex],
    val channels: Map[(Vertex, Vertex), Seq[Vertex]],
    val topology: Map[Vertex, Map[Vertex, Int]],
    val initialTokens: Map[Vertex, Int],
    val impl: Map[Vertex, Vertex],
    val repetitionVector: Seq[Vertex]
) extends DecisionModel {

  override def dominates(o: DecisionModel) = {
    val extra: Boolean = o match {
      case o: SDFApplication => dominatesSdf(o)
      case _                 => true
    }
    super.dominates(o) && extra
  }

  def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size

  val coveredVertexes = {
    for (a <- actors) yield a
    for (d <- delays) yield d
    for ((_, path) <- channels; elem <- path) yield elem
    for ((_, v) <- impl) yield v
  }

  override val uniqueIdentifier = "SDFApplication"

}
