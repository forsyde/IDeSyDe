package idesyde.common

import upickle.default._
import upickle.implicits.key
import idesyde.core.CompleteDecisionModel

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
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

  override def category: String = "RuntimesAndProcessors"

  override def coveredElements: Set[String] = runtimes ++ processors

  override def bodyAsText: String = write(this)

}
