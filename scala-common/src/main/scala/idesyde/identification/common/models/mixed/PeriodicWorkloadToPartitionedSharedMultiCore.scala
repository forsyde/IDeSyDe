package idesyde.identification.common.models.mixed

import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational
import idesyde.identification.models.mixed.WCETComputationMixin
import idesyde.identification.DecisionModel

final case class PeriodicWorkloadToPartitionedSharedMultiCore(
    val workload: CommunicatingExtendedDependenciesPeriodicWorkload,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Array[(String, String)],
    val processSchedulings: Array[(String, String)],
    val channelMappings: Array[(String, String)],
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

  /**
   * since the max utilizations are not vertex themselves, we override it to
   * consider the decision model with most information the dominant one.
   */
  override def dominates[D <: DecisionModel](other: D): Boolean =  other match {
    case o: PeriodicWorkloadToPartitionedSharedMultiCore => 
      super.dominates(other) && o.maxUtilizations.keySet.subsetOf(maxUtilizations.keySet)
    case _ => super.dominates(other)
  }

  def uniqueIdentifier: String = "PeriodicWorkloadToPartitionedSharedMultiCore"
}
