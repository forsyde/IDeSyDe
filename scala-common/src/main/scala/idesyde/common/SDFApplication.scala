package idesyde.common

import scala.jdk.CollectionConverters._

import upickle.default.*
import idesyde.core.DecisionModel
import java.{util => ju}

final case class SDFApplication(
    val actor_minimum_throughputs: Map[String, Double],
    val channel_token_sizes: Map[String, Long],
    val actors_identifiers: Set[String],
    val self_concurrent_actors: Set[String],
    val chain_maximum_latency: Map[String, Map[String, Double]],
    val channels_identifiers: Set[String],
    val topology_channel_names: Vector[Vector[String]],
    val topology_consumption: Vector[Long],
    val topology_dsts: Vector[String],
    val topology_initial_tokens: Vector[Long],
    val topology_token_size_in_bits: Vector[Long],
    val topology_production: Vector[Long],
    val topology_srcs: Vector[String]
) extends DecisionModel
    derives ReadWriter {

  override def category(): String = "SDFApplication"

  override def part(): ju.Set[String] =
    (actors_identifiers ++ channels_identifiers).asJava
  // ++ topology_srcs.zipWithIndex
  //   .map((s, i) =>
  //     s"(${topology_production(i)}, ${topology_consumption(i)}, ${topology_initial_token(i)})=$s:{}-${topology_dsts(i)}:{}"
  //   )
  //   .toSet

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }
}
