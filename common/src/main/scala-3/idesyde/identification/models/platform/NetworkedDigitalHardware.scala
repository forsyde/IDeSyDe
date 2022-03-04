package idesyde.identification.models.platform

import forsyde.io.java.typed.viewers.platform.{
  DigitalModule,
  GenericCommunicationModule,
  GenericMemoryModule,
  GenericProcessingModule,
  InstrumentedCommunicationModule,
  RoundRobinCommunicationModule
}
import idesyde.identification.DecisionModel
import org.apache.commons.math3.fraction.BigFraction
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}
import idesyde.utils.BigFractionIsNumeric

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import org.apache.commons.math3.fraction.Fraction
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import org.jgrapht.opt.graph.sparse.SparseIntUndirectedGraph
import org.jgrapht.alg.util.Pair
import org.jgrapht.opt.graph.sparse.SparseIntUndirectedWeightedGraph
import org.jgrapht.graph.AsWeightedGraph

final case class NetworkedDigitalHardware(
    val processingElems: Array[GenericProcessingModule],
    val communicationElems: Array[GenericCommunicationModule],
    val storageElems: Array[GenericMemoryModule],
    val links: Array[(DigitalModule, DigitalModule)]
)(using Numeric[BigFraction])
    extends DecisionModel {

  val platformElements: Array[DigitalModule] =
    processingElems ++ communicationElems ++ storageElems

  val topology = SparseIntUndirectedGraph(
    platformElements.length,
    links.length,
    () =>
      links
        .map(l =>
          Pair(
            platformElements.indexOf(l._1).asInstanceOf[Integer],
            platformElements.indexOf(l._2).asInstanceOf[Integer]
          )
        )
        .asJavaSeqStream
  )

  lazy val commTopology =
    AsWeightedGraph(
      topology,
      // this function calculates the minimum bandwidth per sec
      (e) => {
        val ceOpt = GenericCommunicationModule
          .safeCast(links(e)._1)
          .or(() => GenericCommunicationModule.safeCast(links(e)._2))
        ceOpt.flatMap(ce => {
          val other = if (links(e)._1 == ce) then links(e)._2 else links(e)._1
          val fraction: BigFraction = RoundRobinCommunicationModule
            .safeCast(ce)
            .map(rrce => {
              BigFraction(
                rrce.getAllocatedWeights.getOrDefault(other.getIdentifier, 1),
                rrce.getTotalWeights
              )
            })
            .orElse(BigFraction.ONE)
          InstrumentedCommunicationModule
            .safeCast(ce)
            .map(insce =>
              fraction
                .multiply(
                  insce.getFlitSizeInBits.toInt * insce.getMaxConcurrentFlits.toInt * insce.getOperatingFrequencyInHertz.toInt
                )
                .divide(insce.getMaxCyclesPerFlit.toInt)
                .reciprocal
                .doubleValue
            )
        }).orElse(0.0)
      },
      true,
      false
    )

  lazy val reversedCommTopology = {
    val maxWeight =
      commTopology.edgeSet.stream.mapToDouble(e => commTopology.getEdgeWeight(e)).max.orElse(0.0)
    AsWeightedGraph(
      commTopology,
      (e) => maxWeight - commTopology.getEdgeWeight(e),
      true,
      false
    )
  }

  // for (pe         <- processingElems) addVertex(pe)
  // for (ce         <- communicationElems) addVertex(ce)
  // for (me         <- storageElems) addVertex(me)
  // for ((src, dst) <- links) addEdge(src, dst)

  val coveredVertexes = {
    for (p <- processingElems) yield p.getViewedVertex
    for (c <- communicationElems) yield c.getViewedVertex
    for (s <- storageElems) yield s.getViewedVertex
  }

  val processingElemsOrdered = processingElems.toList

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

  // def bandWidthBitPerSec(ce: GenericCommunicationModule)(module: DigitalModule): Double =
  //   val fraction: BigFraction = RoundRobinCommunicationModule
  //     .safeCast(ce)
  //     .map(rrce => {
  //       BigFraction(
  //         rrce.getAllocatedWeights.getOrDefault(module.getViewedVertex.getIdentifier, 1),
  //         rrce.getTotalWeights
  //       )
  //     })
  //     .orElse(BigFraction.ONE)
  //   InstrumentedCommunicationModule
  //     .safeCast(ce)
  //     .map(insce =>
  //       fraction
  //         .multiply(
  //           insce.getFlitSizeInBits.toInt * insce.getMaxConcurrentFlits.toInt * insce.getOperatingFrequencyInHertz.toInt
  //         )
  //         .divide(insce.getMaxCyclesPerFlit.toInt)
  //         .floatValue
  //         .ceil
  //     )
  //     .toScala

  lazy val bandWidthBitPerSecMatrix: Map[(GenericCommunicationModule, DigitalModule), Long] =
    Map.empty

  private val pathsAlgorithm = DijkstraManyToManyShortestPaths(topology)

  def inclusiveDirectPaths(src: DigitalModule)(dst: DigitalModule): Seq[DigitalModule] =
    val i = platformElements.indexOf(src)
    val j = platformElements.indexOf(dst)
    val path = pathsAlgorithm.getPath(i, j);
    if (path != null) path.getVertexList.asScala.map(platformElements(_)).toSeq else Seq.empty;

  // def inclusiveDirectCommPaths(src: DigitalModule)(
  //     dst: DigitalModule
  // ): Seq[GenericCommunicationModule] =
  //   // due to the way the graph is constructed, the path
  //   // between different elements must be between comms.
  //   val droppedPath = inclusiveDirectPaths(src)(dst).drop(1).dropRight(1)
  //   if (droppedPath.forall(GenericCommunicationModule.conforms(_))) then
  //     droppedPath.map(GenericCommunicationModule.enforce(_))
  //   else Seq.empty

  // def minTraversalTimePerBit(src: DigitalModule)(dst: DigitalModule)(using
  //     Numeric[BigFraction]
  // ): Option[BigFraction] =
  //   val commPath = inclusiveDirectCommPaths(src)(dst)
  //   // for the first element
  //   val headBW = bandWidthBitPerSec(commPath.head)(src)
  //   // get it 2 by 2 and sum of present, or just return empty
  //   lazy val slidenSum = inclusiveDirectCommPaths(src)(dst)
  //     .sliding(2)
  //     .map(slide =>
  //       slide match
  //         case Seq(ce, cenext, _*) =>
  //           // in case both specify, get the minimum of them
  //           bandWidthBitPerSec(cenext)(ce).orElse(bandWidthBitPerSec(ce)(cenext))
  //         case _ => Option(0L)
  //     )
  //   if (slidenSum.forall(_.isDefined)) then Option(slidenSum.map(_.get).map(BigFraction(1L, _)).sum)
  //   //Option(BigFraction(1, headBW.get).add(pathSum).add(BigFraction(1, tailBW.get)))
  //   else Option.empty
  // for the last element
  //lazy val tailBW = bandWidthBitPerSec(commPath.last)(dst)
  // check if all links are OK
  // if (headBW.isDefined && slidenSum.forall(_.isDefined) && tailBW.isDefined) then
  //   val pathSum = slidenSum.map(_.get).map(BigFraction(1L, _)).sum
  //   Option(BigFraction(1, headBW.get).add(pathSum).add(BigFraction(1, tailBW.get)))
  // else Option.empty

  lazy val minTraversalTimePerBit: Array[Array[BigFraction]] =
    val paths = DijkstraManyToManyShortestPaths(commTopology)
    val maxWeight =
      commTopology.edgeSet.stream.mapToDouble(e => commTopology.getEdgeWeight(e)).max.orElse(0.0)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        if (i != j && paths.getPath(i, j) != null) {
          BigFraction(maxWeight).subtract(BigFraction(paths.getPathWeight(i, j)))
        } else 
          BigFraction.MINUS_ONE        
      })
    })

  lazy val maxTraversalTimePerBit: Array[Array[BigFraction]] = {
    val paths = DijkstraManyToManyShortestPaths(reversedCommTopology)
    platformElements.zipWithIndex.map((src, i) => {
      platformElements.zipWithIndex.map((dst, j) => {
        if (i != j && paths.getPath(i, j) != null) {
          BigFraction(paths.getPathWeight(i, j))
        } else 
          BigFraction.MINUS_ONE        
        // val commPath = inclusiveDirectCommPaths(src)(dst)
        // // for the first element
        // val headBW = bandWidthBitPerSec(commPath.head)(src)
        // // get it 2 by 2 and sum of present, or just return empty
        // lazy val slidenSum = inclusiveDirectCommPaths(src)(dst)
        //   .sliding(2)
        //   .map(slide =>
        //     slide match
        //       case Seq(ce, cenext, _*) =>
        //         // in case both specify, get the minimum of them
        //         bandWidthBitPerSec(cenext)(ce).orElse(bandWidthBitPerSec(ce)(cenext))
        //       case _ => Option(0L)
        //   )
        // if (slidenSum.forall(_.isDefined)) then
        //   Option(slidenSum.map(_.get).map(BigFraction(1L, _)).sum)
        // //Option(BigFraction(1, headBW.get).add(pathSum).add(BigFraction(1, tailBW.get)))
        // else Option.empty
      })
    })
  }

  // lazy val paths: Map[(DigitalModule, DigitalModule), Seq[GenericCommunicationModule]] =
  //   val pathAlgorithm = DijkstraManyToManyShortestPaths(this)
  //   (for (
  //     e <- platformElements;
  //     ee <- platformElements;
  //     if e != ee
  //     // multiple levels of call required since getPath may be null
  //     path  = pathAlgorithm.getPath(e, ee);
  //     vList = if (path != null) path.getVertexList.asScala else Seq.empty;
  //     if !vList.isEmpty;
  //     vPath = vList.filter(_ != e).filter(_ != ee).toSeq;
  //     if vPath.forall(GenericCommunicationModule.conforms(_))
  //   ) yield (e, ee) -> vPath.map(GenericCommunicationModule.safeCast(_).get())).toMap

  override val uniqueIdentifier = "NetworkedDigitalHardware"

}
