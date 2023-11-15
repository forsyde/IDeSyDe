package idesyde.common

import scala.jdk.CollectionConverters._

import upickle.default._
import idesyde.core.DecisionModel
import java.{util => ju}

final case class PartitionedSharedMemoryMultiCore(
    val hardware: SharedMemoryMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends DecisionModel
    derives ReadWriter {

  override def asJsonString(): String = write(this)

  override def asCBORBinary(): Array[Byte] = writeBinary(this)

  override def part(): ju.Set[String] =
    (runtimes.part().asScala ++ hardware.part().asScala).asJava

  def category(): String = "PartitionedSharedMemoryMultiCore"
}
