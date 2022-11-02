package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel

final case class PartitionedCoresWithRuntimes(
    val processors: Array[String],
    val schedulers: Array[String],
    val isBareMetal: Array[Boolean],
    val isFixedPriority: Array[Boolean],
    val isCyclicExecutive: Array[Boolean]
) extends StandardDecisionModel {

  def coveredVertexes          = processors ++ schedulers
  def uniqueIdentifier: String = "PartitionedCoresWithRuntimes"

}
