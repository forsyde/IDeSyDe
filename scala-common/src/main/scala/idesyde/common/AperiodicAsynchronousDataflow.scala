package idesyde.common

import upickle.default.*
import idesyde.core.DecisionModel
import java.{util => ju}

import scala.jdk.CollectionConverters._

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
    val buffer_token_size_in_bits: Map[String, Long],
    val buffers: Set[String],
    val job_graph_name: Vector[String],
    val job_graph_instance: Vector[String],
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
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def category(): String = "AperiodicAsynchronousDataflow"

  override def part(): ju.Set[String] = (processes.toSet ++ buffers.toSet).asJava

}
