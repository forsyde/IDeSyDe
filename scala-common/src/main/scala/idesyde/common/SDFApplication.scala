package idesyde.common

import upickle.default.*
import idesyde.core.CompleteDecisionModel

final case class SDFApplication(
    val actor_computational_needs: Map[String, Map[String, Map[String, Long]]],
    val actor_sizes: Map[String, Long],
    val actors_identifiers: Set[String],
    val channel_num_initial_tokens: Map[String, Long],
    val channel_token_sizes: Map[String, Long],
    val channels_identifiers: Set[String],
    val minimum_actor_throughputs: Map[String, Double],
    val repetition_vector: Map[String, Long],
    val topological_and_heavy_job_ordering: Vector[String],
    val topology_dsts: Vector[String],
    val topology_edge_value: Vector[Long],
    val topology_srcs: Vector[String]
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def uniqueIdentifier: String = "SDFApplication"

  override def coveredElements: Set[String] =
    actors_identifiers ++ channels_identifiers ++ topology_srcs.zipWithIndex
      .map((s, i) => s"${topology_edge_value(i)}=$s:{}-${topology_dsts(i)}:{}")
      .toSet

  override def bodyAsText: String = write(this)

  override def bodyAsBinary: Array[Byte] = writeBinary(this)
}
