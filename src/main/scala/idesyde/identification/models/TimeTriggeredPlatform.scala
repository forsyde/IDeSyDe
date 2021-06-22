package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.interfaces.AbstractProcessingComponent
import forsyde.io.java.typed.interfaces.AbstractCommunicationComponent
import forsyde.io.java.core.VertexInterface

final case class TimeTriggeredPlatform(
    val processingElems: Set[Set[AbstractProcessingComponent]],
    val communicationElems: Set[Set[AbstractCommunicationComponent]],
    val commBetweenProcs: Map[
      (AbstractProcessingComponent, AbstractProcessingComponent),
      Seq[Seq[VertexInterface]]
    ]
) extends DecisionModel {

  def coveredVertexes() = {
    for (pset <- processingElems; p <- pset) yield p
    for (cset <- communicationElems; c <- cset) yield c
  }

  def coveredEdges() = Seq()
}
