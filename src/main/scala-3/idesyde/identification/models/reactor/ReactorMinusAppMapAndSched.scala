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
    reactorMinus.jobGraph.jobs.flatMap(j => 
      platform.hardware.processingElems.map(p => 
        (j, p) -> wcetFunction((j.srcReaction, p))
      )
    ).toMap

  lazy val jobUtilityFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction] =
    reactorMinus.jobGraph.jobs.flatMap(j => 
      platform.hardware.processingElems.map(p => 
        (j, p) -> utilityFunction((j.srcReaction, p))
      )
    ).toMap

//   def wcetFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction] =
//     (for (
//         r <- reactorMinusJobs.jobs;
//         p <- platform.hardware.processingElems;
//         if r.srcReaction.get
//         ) yield {

//             (r, p) -> BigFraction(0)
//         }).toMap

  override val uniqueIdentifier = "ReactorMinusAppMapAndSched"

}
