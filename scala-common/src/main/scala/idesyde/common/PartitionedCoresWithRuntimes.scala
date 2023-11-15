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

  override def asJsonString(): String = write(this)

  override def asCBORBinary(): Array[Byte] = writeBinary(this)
  override def part(): ju.Set[String] =
    ((processors ++ schedulers).toSet ++ (processors.zip(schedulers).toSet).map(_.toString)).asJava

  def category(): String = "PartitionedCoresWithRuntimes"

}
