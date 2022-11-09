package idesyde.identification.common.models.mixed

import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational
import idesyde.identification.models.mixed.WCETComputationMixin

final case class PeriodicWorkloadToPartitionedSharedMultiCore(
    val workload: CommunicatingExtendedDependenciesPeriodicWorkload,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Map[String, String],
    val processSchedulings: Map[String, String],
    val channelMappings: Map[String, String],
    val channelSlotAllocations: Map[String, Map[String, Array[Boolean]]],
    val maxUtilizations: Map[String, Rational]
) extends StandardDecisionModel
    with WCETComputationMixin(workload, platform.hardware) {

  val coveredElements: Set[String] = workload.coveredElements ++ platform.coveredElements

  val coveredElementRelations: Set[(String, String)] =
    workload.coveredElementRelations ++ platform.coveredElementRelations ++
      processSchedulings.toSet ++
      processMappings.toSet ++
      channelMappings.toSet ++
      channelSlotAllocations
        .flatMap[String, String]((channel, slots) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => (channel, ce))
        )
        .toSet

  val wcets = computeWcets

  def uniqueIdentifier: String = "PeriodicWorkloadToPartitionedSharedMultiCore"
}
