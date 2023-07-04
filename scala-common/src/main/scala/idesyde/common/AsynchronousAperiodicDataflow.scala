package idesyde.common

import upickle.default.*
import idesyde.core.DecisionModel
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
final case class AsynchronousAperiodicDataflow(
    val buffer_max_sizes: Map[String, Long],
    val job_graph_buffer_name: Vector[Vector[String]],
    val job_graph_data_read: Vector[Vector[Long]],
    val job_graph_data_sent: Vector[Vector[Long]],
    val job_graph_dst: Vector[Long],
    val job_graph_src: Vector[Long],
    val jobs_of_processes: Vector[String],
    val process_minimum_throughput: Map[String, Double],
    val process_path_maximum_latency: Map[String, Map[String, Double]],
    val processes: Set[String]
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def coveredElements: Set[String] = processes ++ job_graph_buffer_name.flatten.toSet

  override def bodyAsText: String = write(this)

  override def category: String = "AsynchronousAperiodicDataflow"

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

}
