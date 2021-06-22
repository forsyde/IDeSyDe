package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.AbstractProcessingComponent
import forsyde.io.java.typed.viewers.AbstractCommunicationComponent

final case class TimeTriggeredPlatform(
    val processingElems: Set[Set[AbstractProcessingComponent]],
    val communicationElems: Set[Set[AbstractCommunicationComponent]],
    val commBetweenProcs: Map[
      (AbstractProcessingComponent, AbstractProcessingComponent),
      Seq[Seq[Vertex]]
    ]
) extends DecisionModel {

  def coveredVertexes() = {
    for (pset <- processingElems; p <- pset) yield p.getViewedVertex
    for (cset <- communicationElems; c <- cset) yield c.getViewedVertex
  }

  def coveredEdges() = Seq()
}
