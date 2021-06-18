package idesyde.identification.interfaces

import forsyde.io.java.core.VertexInterface
import forsyde.io.java.core.EdgeInterface

trait DecisionModel {

  def dominates(other: DecisionModel): Boolean =
    other
      .coveredVertexes()
      .forall(v => this.coveredVertexes().exists(vv => v.equals(vv))) &&
      other
        .coveredEdges()
        .forall(v => this.coveredEdges().exists(vv => v.equals(vv)))

  def coveredVertexes(): Iterable[VertexInterface]

  def coveredEdges(): Iterable[EdgeInterface]

}
