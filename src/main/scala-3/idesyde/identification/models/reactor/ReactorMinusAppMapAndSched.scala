package idesyde.identification.models.reactor

import idesyde.identification.models.reactor.ReactionJob
import idesyde.identification.DecisionModel
import idesyde.identification.models.SchedulableNetworkedDigHW
import forsyde.io.java.typed.viewers.GenericProcessingModule
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.LinguaFrancaReaction

final case class ReactorMinusAppMapAndSched(
    val reactorMinus: ReactorMinusApplication,
    val platform: SchedulableNetworkedDigHW,
    val wcetFunction: Map[(LinguaFrancaReaction, GenericProcessingModule), BigFraction],
    val utilityFunction: Map[(LinguaFrancaReaction, GenericProcessingModule), BigFraction]
) extends DecisionModel() {

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

  lazy val executionSymmetricRelation: Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- platform.hardware.processingElems;
      pp <- platform.hardware.processingElems;
      // the subset of the WCET function for each process must be identical
      if p == pp || wcetFunction.filter((rp, _) => rp._2 == p) == wcetFunction.filter((rp, _) => rp._2 == pp)
    )
      yield (p, pp)

  lazy val executionSymmetricGroups: Set[Set[GenericProcessingModule]] =
    platform.hardware.processingElems.map(p =>
      platform.hardware.processingElems.filter(pp => executionSymmetricRelation.contains(p, pp))
    )

  lazy val computationallySymmetricGroups: Set[Set[GenericProcessingModule]] =
    for (
      exeSet  <- executionSymmetricGroups;
      topoSet <- platform.topologicallySymmetricGroups;
      d = exeSet.intersect(topoSet);
      if d.size > 0
    ) yield d

  override val uniqueIdentifier = "ReactorMinusAppMapAndSched"

}
