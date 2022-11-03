package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel

final case class SchedulableTiledMultiCore(
    val hardware: TiledMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel {

  val coveredElements         = hardware.coveredElements ++ runtimes.coveredElements
  val coveredElementRelations = hardware.coveredElementRelations ++ runtimes.coveredElementRelations

  val uniqueIdentifier: String = "SchedulableTiledMultiCore"
}
