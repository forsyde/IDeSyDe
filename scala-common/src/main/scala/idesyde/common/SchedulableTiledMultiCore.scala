package idesyde.common

import scala.jdk.CollectionConverters._
import upickle.default.*

import idesyde.core.DecisionModel
import java.{util => ju}

final case class SchedulableTiledMultiCore(
    val hardware: TiledMultiCoreWithFunctions,
    val runtimes: PartitionedCoresWithRuntimes
) extends DecisionModel
    with InstrumentedPlatformMixin[Double]
    derives ReadWriter {

  override def part(): ju.Set[String] = (hardware.part().asScala ++ runtimes.part().asScala).asJava

  def processorsFrequency: Vector[Long] = hardware.processorsFrequency
  def processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    hardware.processorsProvisions

  override def asJsonString(): String = write(this)

  override def asCBORBinary(): Array[Byte] = writeBinary(this)

  def category(): String = "SchedulableTiledMultiCore"
}
