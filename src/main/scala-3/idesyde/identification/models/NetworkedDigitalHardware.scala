package idesyde.identification.models

import idesyde.identification.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.{
  AbstractDigitalModule,
  GenericDigitalInterconnect,
  GenericDigitalStorage,
  GenericProcessingModule
}
import org.jgrapht.graph.{DefaultEdge, DirectedPseudograph, SimpleDirectedGraph}
import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.core.Edge
import forsyde.io.java.typed.viewers.RoundRobinInterconnect
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.GenericMemoryModule

import scala.jdk.OptionConverters.*
import collection.JavaConverters.*
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.SimpleGraph

// type GenericPlatformElement = GenericProcessingModule | GenericDigitalInterconnect | GenericDigitalStorage

final case class NetworkedDigitalHardware(
    val processingElems: Set[GenericProcessingModule],
    val communicationElems: Set[GenericDigitalInterconnect],
    val storageElems: Set[GenericMemoryModule],
    val links: Set[(AbstractDigitalModule, AbstractDigitalModule)]
) extends SimpleGraph[AbstractDigitalModule, DefaultEdge](classOf[DefaultEdge])
    with DecisionModel {

  for (pe <- processingElems) addVertex(pe)
  for (ce <- communicationElems) addVertex(ce)
  for (me <- storageElems) addVertex(me)
  // TODO: error here at creation
  for ((src, dst) <- links) addEdge(src, dst)

  val coveredVertexes = {
    for (p <- processingElems) yield p.getViewedVertex
    for (c <- communicationElems) yield c.getViewedVertex
    for (s <- storageElems) yield s.getViewedVertex
  }

  val platformElements: Set[AbstractDigitalModule] =
    processingElems ++ communicationElems ++ storageElems

  lazy val processingElemsOrdered = processingElems.toList

  val allocatedBandwidthFraction: Map[(GenericDigitalInterconnect, GenericProcessingModule), BigFraction] =
    (for (
      ce <- communicationElems;
      pe <- processingElems;
      rrOpt = RoundRobinInterconnect.safeCast(ce).toScala
    ) yield rrOpt match {
      case Some(rr) =>
        (ce, pe) -> BigFraction(
          // the ID has to be taken from the vertex directly dues to viewers
          // prefixing the IDs in the sake of unambiguity
          rr.getAllocatedWeights.getOrDefault(pe.getViewedVertex.getIdentifier, 0),
          rr.getTotalWeights
        )
      case _ => (ce, pe) -> BigFraction(0)
    }).toMap

  val bandWidthBitPerSec: Map[(GenericDigitalInterconnect, GenericProcessingModule), Long] =
    allocatedBandwidthFraction.map((ce2pe, frac) => {
      val ce = ce2pe._1
      // TODO this computation might not be numerically stable for large numbers. to double check later.
      ce2pe -> frac
        .multiply(
          ce.getMaxFlitSizeInBits.toInt * ce.getMaxConcurrentFlits * ce.getNominalFrequencyInHertz.toInt
        )
        .longValue
    })

  lazy val paths: Map[(AbstractDigitalModule, AbstractDigitalModule), Seq[GenericDigitalInterconnect]] =
    val pathAlgorithm = FloydWarshallShortestPaths(this)
    (for (
      e <- platformElements; ee <- platformElements - e;
      // multiple levels of call required since getPath may be null
      path  = pathAlgorithm.getPath(e, ee);
      vList = if (path != null) path.getVertexList.asScala else Seq.empty;
      if !vList.isEmpty;
      vPath = vList.filter(_ != e).filter(_ != ee).toSeq;
      if vPath.forall(GenericDigitalInterconnect.conforms(_))
    ) yield (e, ee) -> vPath.map(GenericDigitalInterconnect.safeCast(_).get())).toMap

  override val uniqueIdentifier = "NetworkedDigitalHardware"

}
