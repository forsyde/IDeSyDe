package idesyde.common.legacy

import scala.jdk.CollectionConverters._

import upickle.default.*

import idesyde.core.DecisionModel
import java.{util => ju}

final case class PartitionedCoresWithRuntimes(
    val processors: Vector[String],
    val schedulers: Vector[String],
    val is_bare_metal: Vector[Boolean],
    val is_fixed_priority: Vector[Boolean],
    val is_cyclic_executive: Vector[Boolean]
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }
  override def part(): ju.Set[String] =
    ((processors ++ schedulers).toSet ++ (processors.zip(schedulers).toSet).map(_.toString)).asJava

  override def category(): String = "PartitionedCoresWithRuntimes"

}
