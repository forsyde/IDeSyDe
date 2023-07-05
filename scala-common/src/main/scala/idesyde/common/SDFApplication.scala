package idesyde.common

import upickle.default.*
import idesyde.core.CompleteDecisionModel

final case class SDFApplication(
    val actor_minimum_throughputs: Map[String, Double],
    val actors_identifiers: Set[String],
    val self_concurrent_actors: Set[String],
    val chain_maximum_latency: Map[String, Map[String, Double]],
    val channels_identifiers: Set[String],
    val topology_channel_names: Vector[Vector[String]],
    val topology_consumption: Vector[Long],
    val topology_dsts: Vector[String],
    val topology_initial_token: Vector[Long],
    val topology_production: Vector[Long],
    val topology_srcs: Vector[String]
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def category: String = "SDFApplication"

  override def coveredElements: Set[String] =
    actors_identifiers ++ channels_identifiers
  // ++ topology_srcs.zipWithIndex
  //   .map((s, i) =>
  //     s"(${topology_production(i)}, ${topology_consumption(i)}, ${topology_initial_token(i)})=$s:{}-${topology_dsts(i)}:{}"
  //   )
  //   .toSet

  override def bodyAsText: String = write(this)

  override def bodyAsBinary: Array[Byte] = writeBinary(this)
}
