package idesyde.identification.common.models.mixed

import idesyde.identification.common.models.workload.PeriodicDependentWorkload
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.StandardDecisionModel

final case class PeriodicWorkloadToPartitionedSharedMultiCore(
    val workload: PeriodicDependentWorkload,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Array[String],
    val processSchedulings: Array[String],
    val channelMappings: Array[String],
    val channelSlotAllocations: Array[Map[String, Array[Boolean]]]
) extends StandardDecisionModel {

  val coveredElements: Set[String] = workload.coveredElements ++ platform.coveredElements

  val coveredElementRelations: Set[(String, String)] =
    workload.coveredElementRelations ++ platform.coveredElementRelations ++
      workload.processes.zip(processSchedulings) ++
      workload.processes.zip(processMappings) ++
      workload.channels.zip(channelMappings) ++
      channelSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => workload.channels(i) -> ce)
      )

  def uniqueIdentifier: String = "PeriodicWorkloadToPartitionedSharedMultiCore"
}
