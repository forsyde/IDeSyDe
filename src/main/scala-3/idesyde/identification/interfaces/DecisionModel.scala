package idesyde.identification.interfaces

import forsyde.io.java.core.Edge
import forsyde.io.java.core.Vertex


trait DecisionModel {

  def dominates(other: DecisionModel): Boolean =
    other
      .coveredVertexes()
      .forall(v => this.coveredVertexes().exists(vv => v.equals(vv)))
//      && other
//        .coveredEdges()
//        .forall(v => this.coveredEdges().exists(vv => v.equals(vv)))

  def coveredVertexes(): Iterable[Vertex]

}
