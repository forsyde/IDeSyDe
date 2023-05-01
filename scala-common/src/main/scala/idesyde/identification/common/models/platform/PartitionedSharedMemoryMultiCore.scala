package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel
import upickle.default._
import idesyde.core.CompleteDecisionModel

final case class PartitionedSharedMemoryMultiCore(
    val hardware: SharedMemoryMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  override def bodyAsText: String = write(this)

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

  val coveredElements         = runtimes.coveredElements ++ hardware.coveredElements
  val coveredElementRelations = hardware.coveredElementRelations ++ runtimes.coveredElementRelations

  val uniqueIdentifier: String = "PartitionedSharedMemoryMultiCore"
}
