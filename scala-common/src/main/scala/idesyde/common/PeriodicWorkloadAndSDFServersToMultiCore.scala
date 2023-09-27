package idesyde.common

import upickle.default._

import idesyde.core.CompleteDecisionModel

final case class PeriodicWorkloadAndSDFServerToMultiCore(
    val tasksAndSDFs: PeriodicWorkloadAndSDFServers,
    val platform: PartitionedSharedMemoryMultiCore,
    val processesSchedulings: Vector[(String, String)],
    val processesMappings: Vector[(String, String)],
    val messagesMappings: Vector[(String, String)],
    val messageSlotAllocations: Map[String, Map[String, Vector[Boolean]]],
    val sdfServerUtilization: Vector[Double],
    val sdfOrderBasedSchedules: Vector[Vector[String]]
) extends StandardDecisionModel
    with CompleteDecisionModel
    with WCETComputationMixin(tasksAndSDFs, platform.hardware)
    derives ReadWriter {

  def bodyAsText: String = write(this)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  val coveredElements =
    tasksAndSDFs.coveredElements ++ platform.coveredElements ++ (processesMappings.toSet ++ messagesMappings.toSet ++
      messageSlotAllocations
        .flatMap((channel, slots) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => (channel, ce))
        )
        .toSet).map(_.toString)

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = tasksAndSDFs.messagesMaxSizes

  val wcets = computeWcets

  val category: String = "PeriodicWorkloadAndSDFServerToMultiCore"
}
