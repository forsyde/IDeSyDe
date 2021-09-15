package idesyde.identification

import forsyde.io.java.core.Edge
import forsyde.io.java.core.Vertex


trait DecisionModel {

  val coveredVertexes: Iterable[Vertex]

  val uniqueIdentifier: String

  def dominates(other: DecisionModel): Boolean =
    other
      .coveredVertexesAsSet
      .subsetOf(coveredVertexesAsSet)

  override lazy val hashCode: Int =
    coveredVertexes.map(v => v.hashCode).sum + uniqueIdentifier.hashCode

  lazy val coveredVertexesAsSet: Set[Vertex] = coveredVertexes.toSet

}
