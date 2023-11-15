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

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def part(): ju.Set[String] =
    (runtimes.part().asScala ++ hardware.part().asScala).asJava

  override def category(): String = "PartitionedSharedMemoryMultiCore"
}
