package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.{AbstractDigitalModule, GenericDigitalInterconnect, GenericDigitalStorage, GenericProcessingModule}
import org.jgrapht.graph.{DefaultEdge, DirectedPseudograph, SimpleDirectedGraph}
import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.core.Edge

// type GenericPlatformElement = GenericProcessingModule | GenericDigitalInterconnect | GenericDigitalStorage

final case class NetworkedDigitalHardware(
    val processingElems: Set[GenericProcessingModule],
    val communicationElems: Set[GenericDigitalInterconnect],
    val storageElems: Set[GenericDigitalStorage],
    val links: Set[(AbstractDigitalModule, AbstractDigitalModule)],
    val paths: Map[(AbstractDigitalModule, AbstractDigitalModule), Seq[GenericDigitalInterconnect]]
) extends SimpleDirectedGraph[AbstractDigitalModule, DefaultEdge](classOf[DefaultEdge]) with DecisionModel {

  for (pe <- processingElems) addVertex(pe)
  for (ce <- communicationElems) addVertex(ce)
  for (me <- storageElems) addVertex(me)
  // TODO: error here at creation
  for ((src, dst) <- links) addEdge(src, dst)

  def coveredVertexes() = {
    for (p <- processingElems) yield p.getViewedVertex
    for (c <- communicationElems) yield c.getViewedVertex
    for (s <- storageElems) yield s.getViewedVertex
  }

  def coveredEdges() = Set.empty
}
