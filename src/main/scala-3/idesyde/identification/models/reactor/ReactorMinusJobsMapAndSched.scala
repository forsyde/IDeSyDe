package idesyde.identification.models.reactor

import idesyde.identification.models.reactor.ReactionJob
import idesyde.identification.DecisionModel
import idesyde.identification.models.SchedulableNetworkedDigHW
import forsyde.io.java.typed.viewers.GenericProcessingModule
import org.apache.commons.math3.fraction.BigFraction

final case class ReactorMinusJobsMapAndSched(
    val reactorMinus: ReactorMinusApplication,
    val platform: SchedulableNetworkedDigHW,
    val wcetFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction],
    val utilityFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction]
) extends DecisionModel() {

  val coveredVertexes = reactorMinus.coveredVertexes ++ platform.coveredVertexes

//   def wcetFunction: Map[(ReactionJob, GenericProcessingModule), BigFraction] =
//     (for (
//         r <- reactorMinusJobs.jobs;
//         p <- platform.hardware.processingElems;
//         if r.srcReaction.get
//         ) yield {

//             (r, p) -> BigFraction(0)
//         }).toMap

  override val uniqueIdentifier = "ReactorMinusJobsMapAndSched"

}
