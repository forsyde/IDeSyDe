package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel

final case class PartitionedSharedMemoryMultiCore(
    val hardware: SharedMemoryMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel {

  val coveredElements         = runtimes.coveredElements ++ hardware.coveredElements
  val coveredElementRelations = hardware.coveredElementRelations ++ runtimes.coveredElementRelations

  val uniqueIdentifier: String = "PartitionedSharedMemoryMultiCore"
}
