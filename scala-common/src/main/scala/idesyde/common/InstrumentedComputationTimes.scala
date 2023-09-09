package idesyde.common

import upickle.default._

import idesyde.core.CompleteDecisionModel

final case class InstrumentedComputationTimes(
    val processes: Set[String],
    val processing_elements: Set[String],
    val best_execution_times: Map[String, Map[String, Long]],
    val average_execution_times: Map[String, Map[String, Long]],
    val worst_execution_times: Map[String, Map[String, Long]],
    val scale_factor: Long
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

  override def category: String = "InstrumentedComputationTimes"

  override def coveredElements: Set[String] = processes ++ processing_elements

  override def bodyAsText: String = write(this)

}
