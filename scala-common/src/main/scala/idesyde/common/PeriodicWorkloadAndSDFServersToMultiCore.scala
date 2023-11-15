package idesyde.common

import scala.jdk.CollectionConverters._

import upickle.default._

import idesyde.core.DecisionModel
import java.{util => ju}

final case class PeriodicWorkloadAndSDFServerToMultiCore(
    val tasksAndSDFs: PeriodicWorkloadAndSDFServers,
    val platform: PartitionedSharedMemoryMultiCore,
    val processesSchedulings: Vector[(String, String)],
    val processesMappings: Vector[(String, String)],
    val messagesMappings: Vector[(String, String)],
    val messageSlotAllocations: Map[String, Map[String, Vector[Boolean]]],
    val sdfServerUtilization: Vector[Double],
    val sdfOrderBasedSchedules: Vector[Vector[String]]
) extends DecisionModel
    with WCETComputationMixin(tasksAndSDFs, platform.hardware)
    derives ReadWriter {

  override def asJsonString(): String = write(this)

  override def asCBORBinary(): Array[Byte] = writeBinary(this)

  override def part(): ju.Set[String] =
    (tasksAndSDFs
      .part()
      .asScala ++ platform.part().asScala ++ (processesMappings.toSet ++ messagesMappings.toSet ++
      messageSlotAllocations
        .flatMap((channel, slots) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => (channel, ce))
        )
        .toSet).map(_.toString)).asJava

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = tasksAndSDFs.messagesMaxSizes

  val wcets = computeWcets

  def category(): String = "PeriodicWorkloadAndSDFServerToMultiCore"
}
