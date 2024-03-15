package idesyde.common.legacy

import upickle.default.*
import idesyde.core.DecisionModel
import java.{util => ju}

import scala.jdk.CollectionConverters._

/** Decision model for analysed synchronous dataflow graphs.
  *
  * Aside from the same information in the original SDF application, it also includes liveness
  * information like its repetition vector.
  */
final case class AnalysedSDFApplication(
    val periodic_admissible_static_schedule: Seq[String],
    val repetition_vector: Map[String, Long],
    val sdf_application: SDFApplication
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def part(): ju.Set[String] = sdf_application.part()

  override def category(): String = "AnalysedSDFApplication"

}
