package idesyde.identification.interfaces

import forsyde.io.java.core.Vertex
import forsyde.io.java.core.Edge

trait DecisionModel {

  def dominates(other: DecisionModel): Boolean =
    other
      .coveredVertexes()
      .forall(v => this.coveredVertexes().exists(vv => v.equals(vv))) &&
      other
        .coveredEdges()
        .forall(v => this.coveredEdges().exists(vv => v.equals(vv)))

  def coveredVertexes(): Iterable[Vertex]

  def coveredEdges(): Iterable[Edge]

}
