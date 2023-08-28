package idesyde.common

import upickle.default.*
import idesyde.core.CompleteDecisionModel

/** This decision model abstract asynchronous dataflow models that can be described by a repeating
  * job-graph of this asynchronous processes. Two illustratives dataflow models fitting this
  * category are synchronous dataflow models (despite the name) and cyclo-static dataflow models.
  *
  * Assumptions: 1. the job graph is always ready to be executed; or, the model is aperiodic.
  *
  * 2. executing the job graph as presented guarantees that the dataflow processes are live (never
  * deadlocked).
  *
  * 3. The job graph ois weakly connected. If you wish to have multiple "applications", you should
  * generate one decision model for each application.
  */
final case class AperiodicAsynchronousDataflow(
    val buffer_max_size_in_bits: Map[String, Long],
    val buffers: Set[String],
    val job_graph_dst_instance: Vector[Long],
    val job_graph_dst_name: Vector[String],
    val job_graph_is_strong_precedence: Vector[Boolean],
    val job_graph_src_instance: Vector[Long],
    val job_graph_src_name: Vector[String],
    val process_get_from_buffer_in_bits: Map[String, Map[String, Long]],
    val process_minimum_throughput: Map[String, Double],
    val process_path_maximum_latency: Map[String, Map[String, Double]],
    val process_put_in_buffer_in_bits: Map[String, Map[String, Long]],
    val processes: Set[String]
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def bodyAsText: String = write(this)

  override def category: String = "AperiodicAsynchronousDataflow"

  override def coveredElements: Set[String] = processes.toSet ++ buffers.toSet

  override def bodyAsBinary: Array[Byte] = writeBinary(this)
}
