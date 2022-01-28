package idesyde.identification.models.platform

import forsyde.io.java.typed.viewers.platform.{DigitalModule, GenericCommunicationModule, GenericMemoryModule, GenericProcessingModule, InstrumentedCommunicationModule, RoundRobinCommunicationModule}
import idesyde.identification.DecisionModel
import org.apache.commons.math3.fraction.BigFraction
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}
import idesyde.utils.BigFractionIsNumeric

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import org.apache.commons.math3.fraction.Fraction
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths

final case class NetworkedDigitalHardware(
    val processingElems: Set[GenericProcessingModule],
    val communicationElems: Set[GenericCommunicationModule],
    val storageElems: Set[GenericMemoryModule],
    val links: Set[(DigitalModule, DigitalModule)]
) extends SimpleGraph[DigitalModule, DefaultEdge](classOf[DefaultEdge])
    with DecisionModel {

  given Numeric[BigFraction] = BigFractionIsNumeric()

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

  val platformElements: Set[DigitalModule] =
    processingElems ++ communicationElems ++ storageElems

  lazy val processingElemsOrdered = processingElems.toList

  val allocatedBandwidthFraction
      : Map[(GenericCommunicationModule, GenericProcessingModule), BigFraction] =
    (for (
      ce <- communicationElems;
      pe <- processingElems;
      rrOpt = RoundRobinCommunicationModule.safeCast(ce).toScala
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

  def bandWidthBitPerSec(ce: GenericCommunicationModule)(module: DigitalModule): Option[Long] =
    if (containsEdge(module, ce) || containsEdge(ce, module))
      val fraction: BigFraction = RoundRobinCommunicationModule.safeCast(ce).map(rrce => {
        BigFraction(
          rrce.getAllocatedWeights.getOrDefault(module.getViewedVertex.getIdentifier, 1),
          rrce.getTotalWeights)
        }).orElse(BigFraction.ONE)
      InstrumentedCommunicationModule.safeCast(ce).map(insce =>
        fraction
          .multiply(insce.getFlitSizeInBits.toInt * insce.getMaxConcurrentFlits.toInt * insce.getOperatingFrequencyInHertz.toInt)
          .divide(insce.getMaxCyclesPerFlit.toInt)
          .floatValue.ceil
          .toLong
      ).toScala
    else
      Option(0)

  lazy val bandWidthBitPerSecMatrix: Map[(GenericCommunicationModule, DigitalModule), Long] = Map.empty

  private val pathsAlgorithm = DijkstraManyToManyShortestPaths(this)

  def directPaths(src: DigitalModule)(dst: DigitalModule): Seq[DigitalModule] =
    val path  = pathsAlgorithm.getPath(src, dst);
    if (path != null) path.getVertexList.asScala.toSeq.drop(1).dropRight(1) else Seq.empty;

  def directCommPaths(src: DigitalModule)(dst: DigitalModule): Seq[GenericCommunicationModule] =
    // due to the way the graph is constructed, the path
    // between different elements must be between comms.
    if (directPaths(src)(dst).forall(GenericCommunicationModule.conforms(_))) then
      directPaths(src)(dst).map(GenericCommunicationModule.enforce(_))
    else
      Seq.empty

  def minTraversalTimePerBit(src: DigitalModule)(dst: DigitalModule)(using Numeric[BigFraction]): Option[BigFraction] =
    val commPath = directCommPaths(src)(dst)
    // for the first element
    val headBW = bandWidthBitPerSec(commPath.head)(src)
    // get it 2 by 2 and sum of present, or just return empty
    lazy val slidenSum = directCommPaths(src)(dst).sliding(2).map(slide =>
      slide match
        case Seq(ce, cenext, _*) => 
          // in case both specify, get the minimum of them
          bandWidthBitPerSec(cenext)(ce).flatMap(b1 => bandWidthBitPerSec(ce)(cenext).map(b2 => scala.math.min(b1, b2)))
        case _ => Option(0L)
    )
    // for the last element
    lazy val tailBW = bandWidthBitPerSec(commPath.last)(dst)
    // check if all links are OK
    if (headBW.isDefined && slidenSum.forall(_.isDefined) && tailBW.isDefined) then
      val pathSum = slidenSum.map(_.get).map(BigFraction(1L, _)).sum
      Option(BigFraction(1, headBW.get).add(pathSum).add(BigFraction(1, tailBW.get)))
    else Option.empty

  lazy val minTraversalTimePerBitMatrix: Map[(DigitalModule, DigitalModule), BigFraction] =
    (for
      e <- platformElements
      ee <- platformElements - e
      t = minTraversalTimePerBit(e)(ee)
      if t.isDefined
    yield
      (e, ee) -> t.get)
    .toMap

  lazy val paths: Map[(DigitalModule, DigitalModule), Seq[GenericCommunicationModule]] =
    val pathAlgorithm = DijkstraManyToManyShortestPaths(this)
    (for (
      e <- platformElements; ee <- platformElements - e;
      // multiple levels of call required since getPath may be null
      path  = pathAlgorithm.getPath(e, ee);
      vList = if (path != null) path.getVertexList.asScala else Seq.empty;
      if !vList.isEmpty;
      vPath = vList.filter(_ != e).filter(_ != ee).toSeq;
      if vPath.forall(GenericCommunicationModule.conforms(_))
    ) yield (e, ee) -> vPath.map(GenericCommunicationModule.safeCast(_).get())).toMap

  override val uniqueIdentifier = "NetworkedDigitalHardware"

  

}
