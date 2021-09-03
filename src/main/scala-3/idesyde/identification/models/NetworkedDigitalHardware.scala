package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import org.jgrapht.graph.DirectedPseudograph
import forsyde.io.java.core.ForSyDeModel
import org.jgrapht.graph.DefaultEdge
import forsyde.io.java.core.Edge

type GenericPlatformElement = GenericProcessingModule | GenericDigitalInterconnect | GenericDigitalStorage

final case class NetworkedDigitalHardware(
    val processingElems: Set[GenericProcessingModule],
    val communicationElems: Set[GenericDigitalInterconnect],
    val storageElems: Set[GenericDigitalStorage],
    val links: Set[Edge],
    val paths: Map[(GenericPlatformElement, GenericPlatformElement), Seq[GenericDigitalInterconnect]]
) extends DecisionModel {

  def coveredVertexes() = {
    for (p <- processingElems) yield p.getViewedVertex
    for (c <- communicationElems) yield c.getViewedVertex
    for (s <- storageElems) yield s.getViewedVertex
  }

  def coveredEdges() = {
    for(l <- links) yield l
  }
}
