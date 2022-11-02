package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.forsyde.models.platform.SharedMemoryMultiCore

final case class PartitionedSharedMemoryMultiCore(
    val hardware: SharedMemoryMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel {

  def coveredVertexes = runtimes.coveredVertexes ++ hardware.coveredVertexes

  def uniqueIdentifier: String = "PartitionedSharedMemoryMultiCore"
}
