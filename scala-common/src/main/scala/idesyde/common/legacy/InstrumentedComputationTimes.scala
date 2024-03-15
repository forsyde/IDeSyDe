package idesyde.common.legacy

import scala.jdk.CollectionConverters._

import upickle.default._

import idesyde.core.DecisionModel
import java.{util => ju}

final case class InstrumentedComputationTimes(
    val processes: Set[String],
    val processing_elements: Set[String],
    val best_execution_times: Map[String, Map[String, Long]],
    val average_execution_times: Map[String, Map[String, Long]],
    val worst_execution_times: Map[String, Map[String, Long]],
    val scale_factor: Long
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def category(): String = "InstrumentedComputationTimes"

  override def part(): ju.Set[String] = (processes ++ processing_elements).asJava

}
