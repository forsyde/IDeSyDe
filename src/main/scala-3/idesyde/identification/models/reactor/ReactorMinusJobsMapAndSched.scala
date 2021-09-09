package idesyde.identification.models.reactor

import idesyde.identification.models.reactor.ReactionJob
import idesyde.identification.DecisionModel
import idesyde.identification.models.SchedulableNetworkedDigHW
import forsyde.io.java.typed.viewers.GenericProcessingModule
import org.apache.commons.math3.fraction.Fraction

final case class ReactorMinusJobsMapAndSched(
    val reactorMinusJobs: ReactorMinusJobs,
    val platform: SchedulableNetworkedDigHW,
    val wcetFunction: Map[(ReactionJob, GenericProcessingModule), Fraction]
) extends DecisionModel() {

  def coveredVertexes = reactorMinusJobs.coveredVertexes ++ platform.coveredVertexes

//   def wcetFunction: Map[(ReactionJob, GenericProcessingModule), Fraction] =
//     (for (
//         r <- reactorMinusJobs.jobs; 
//         p <- platform.hardware.processingElems;
//         if r.srcReaction.get
//         ) yield {

//             (r, p) -> Fraction(0)
//         }).toMap

}
