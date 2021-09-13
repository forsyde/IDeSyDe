package idesyde.identification

import forsyde.io.java.core.Edge
import forsyde.io.java.core.Vertex


trait DecisionModel {

  val coveredVertexes: Iterable[Vertex]

  def dominates(other: DecisionModel): Boolean =
    other
      .coveredVertexesAsSet
      .subsetOf(coveredVertexesAsSet)

  lazy val coveredVertexesAsSet: Set[Vertex] = coveredVertexes.toSet

}
