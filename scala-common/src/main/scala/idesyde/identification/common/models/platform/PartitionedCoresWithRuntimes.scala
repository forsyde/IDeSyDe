package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel

final case class PartitionedCoresWithRuntimes(
    val processors: Vector[String],
    val schedulers: Vector[String],
    val isBareMetal: Vector[Boolean],
    val isFixedPriority: Vector[Boolean],
    val isCyclicExecutive: Vector[Boolean]
) extends StandardDecisionModel {

  val coveredElements          = (processors ++ schedulers).toSet
  val coveredElementRelations  = processors.zip(schedulers).toSet
  val uniqueIdentifier: String = "PartitionedCoresWithRuntimes"

}