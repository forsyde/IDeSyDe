package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.forsyde.models.platform.SharedMemoryMultiCore

final case class PartitionedSharedMemoryMultiCore(
    val hardware: SharedMemoryMultiCore,
    val schedulers: Array[String],
    val isFixedPriority: Array[Boolean],
    val isCyclicExecutive: Array[Boolean]
) extends StandardDecisionModel {

  def coveredVertexes = schedulers ++ hardware.coveredVertexes

  def uniqueIdentifier: String = "PartitionedSharedMemoryMultiCore"
}
