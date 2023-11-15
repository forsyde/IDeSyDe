package idesyde.common

import scala.jdk.CollectionConverters._

import upickle.default.*

import idesyde.core.DecisionModel
import java.{util => ju}

final case class PartitionedCoresWithRuntimes(
    val processors: Vector[String],
    val schedulers: Vector[String],
    val isBareMetal: Vector[Boolean],
    val isFixedPriority: Vector[Boolean],
    val isCyclicExecutive: Vector[Boolean]
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }
  override def part(): ju.Set[String] =
    ((processors ++ schedulers).toSet ++ (processors.zip(schedulers).toSet).map(_.toString)).asJava

  override def category(): String = "PartitionedCoresWithRuntimes"

}
