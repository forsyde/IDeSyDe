package idesyde.identification.models.reactor

import forsyde.io.java.typed.viewers.moc.linguafranca.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import idesyde.identification.models.reactor.ReactionJob
import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.platform.SchedulableNetworkedDigHW
import org.apache.commons.math3.fraction.BigFraction
import org.jgrapht.graph.SimpleGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector

import collection.JavaConverters.*
import java.util.stream.Collectors
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.AsSubgraph

final case class ReactorMinusAppMapAndSched(
    val reactorMinus: ReactorMinusApplication,
    val platform: SchedulableNetworkedDigHW,
    val wcetFunction: Map[(LinguaFrancaReaction, GenericProcessingModule), BigFraction],
    val utilityFunction: Map[(LinguaFrancaReaction, GenericProcessingModule), BigFraction]
) extends ForSyDeDecisionModel() {

  // for (r <- reactorMinus.reactions; p <- platform.hardware.processingElems)
  //   println(s"${r.getIdentifier}, ${p.getIdentifier}, ${wcetFunction.getOrElse((r, p), BigFraction.ZERO).toString}")

  val coveredVertexes = reactorMinus.coveredVertexes ++ platform.coveredVertexes

  lazy val jobWcetFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction] =
    reactorMinus.jobGraph.jobs
      .flatMap(j =>
        platform.hardware.processingElems.map(p => (j, p) -> wcetFunction((j.srcReaction, p)))
      )
      .toMap

  lazy val jobUtilityFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction] =
    reactorMinus.jobGraph.jobs
      .flatMap(j =>
        platform.hardware.processingElems.map(p => (j, p) -> utilityFunction((j.srcReaction, p)))
      )
      .toMap

  lazy val executionSymmetricRelationGraph: SimpleGraph[GenericProcessingModule, DefaultEdge] =
    val graph = SimpleGraph[GenericProcessingModule, DefaultEdge](classOf[DefaultEdge])
    platform.hardware.processingElems.foreach(p => graph.addVertex(p))
    for (
      p  <- platform.hardware.processingElems;
      pp <- platform.hardware.processingElems;
      if p != pp;
      // the subset of the WCET function for each process must be identical
      if reactorMinus.reactions.forall(r =>
        // either both cannot execute the reaction or they must have identical WCET
        (!wcetFunction.contains((r, p)) && !wcetFunction.contains((r, pp))) ||
          wcetFunction.contains((r, p)) == wcetFunction.contains((r, pp))
      )
    ) graph.addEdge(p, pp)
    graph

  lazy val executionSymmetricGroups: Set[Set[GenericProcessingModule]] =
    ConnectivityInspector(executionSymmetricRelationGraph).connectedSets.stream
      .map(g => g.asScala.toSet)
      .collect(Collectors.toSet)
      .asScala
      .toSet

  lazy val computationallySymmetricGroups: Set[Set[GenericProcessingModule]] =
    val intersectGraph = AsSubgraph(executionSymmetricRelationGraph)
    executionSymmetricRelationGraph.edgeSet.stream.forEach(e =>
      val src = executionSymmetricRelationGraph.getEdgeSource(e)
      val dst = executionSymmetricRelationGraph.getEdgeTarget(e)
      if (!platform.topologySymmetryRelationGraph.containsEdge(src, dst))
        intersectGraph.removeEdge(e)
    )
    ConnectivityInspector(intersectGraph).connectedSets.stream
      .map(g => g.asScala.toSet)
      .collect(Collectors.toSet)
      .asScala
      .toSet

  /** The min number of processing cores is ideally the rank of the 'boolean' matrix formed by th
    * WCET function.
    *
    * @return
    *   the min number of processing cores
    */
  lazy val minProcessingCores: Int =
    platform.hardware.processingElems
      .map(p => reactorMinus.reactions.filter(r => wcetFunction.contains((r, p))))
      .size

  override val uniqueIdentifier = "ReactorMinusAppMapAndSched"

}
