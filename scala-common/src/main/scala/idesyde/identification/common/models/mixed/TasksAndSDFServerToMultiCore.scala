package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.models.mixed.WCETComputationMixin
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore

final case class TasksAndSDFServerToMultiCore(
    val sdfandtask: TaskdAndSDFServer,
    val platform: PartitionedSharedMemoryMultiCore,
    val processesMappings: Vector[(String, String)],
    val messagesMappings: Vector[(String, String)],
    val messageSlotAllocations: Map[String, Map[String, Vector[Boolean]]]
) extends StandardDecisionModel
    with WCETComputationMixin(sdfandtask, platform.hardware) {

  val coveredElements = sdfandtask.coveredElements ++ platform.coveredElements
  val coveredElementRelations =
    sdfandtask.coveredElementRelations ++ platform.coveredElementRelations ++
      processesMappings.toSet ++ messagesMappings.toSet ++
      messageSlotAllocations
        .flatMap((channel, slots) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => (channel, ce))
        )
        .toSet

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = sdfandtask.messagesMaxSizes

  val wcets = computeWcets

  val uniqueIdentifier: String = "TasksAndSDFServerToMultiCore"
}
