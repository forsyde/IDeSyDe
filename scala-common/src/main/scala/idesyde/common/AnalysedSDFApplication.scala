package idesyde.common

import upickle.default.*
import idesyde.core.DecisionModel
import idesyde.core.CompleteDecisionModel

/** Decision model for analysed synchronous dataflow graphs.
  *
  * Aside from the same information in the original SDF application, it also includes liveness
  * information like its repetition vector.
  */
final case class AnalysedSDFApplication(
    val periodic_admissible_static_schedule: Seq[String],
    val repetition_vector: Map[String, Long],
    val sdf_application: SDFApplication
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def bodyAsText: String = write(this)

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

  override def coveredElements: Set[String] = sdf_application.coveredElements

  override def category: String = "AnalysedSDFApplication"

}
