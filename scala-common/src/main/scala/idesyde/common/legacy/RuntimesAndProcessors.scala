package idesyde.common.legacy

import scala.jdk.CollectionConverters._

import upickle.default._
import upickle.implicits.key
import idesyde.core.DecisionModel
import java.{util => ju}

final case class RuntimesAndProcessors(
    @key("runtimes") val runtimes: Set[String],
    @key("processors") val processors: Set[String],
    @key("runtime_host") val runtime_host: Map[String, String],
    @key("processor_affinities") val processor_affinities: Map[String, String],
    @key("is_bare_metal") val is_bare_metal: Set[String],
    @key("is_fixed_priority") val is_fixed_priority: Set[String],
    @key("is_preemptive") val is_preemptive: Set[String],
    @key("is_earliest_deadline_first") val is_earliest_deadline_first: Set[String],
    @key("is_super_loop") val is_super_loop: Set[String]
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def category(): String = "RuntimesAndProcessors"

  override def part(): ju.Set[String] = (runtimes ++ processors).asJava

}
