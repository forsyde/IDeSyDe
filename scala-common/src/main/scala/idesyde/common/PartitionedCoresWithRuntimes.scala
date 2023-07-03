package idesyde.common

import upickle.default.*

import idesyde.core.CompleteDecisionModel

final case class PartitionedCoresWithRuntimes(
    val processors: Vector[String],
    val schedulers: Vector[String],
    val isBareMetal: Vector[Boolean],
    val isFixedPriority: Vector[Boolean],
    val isCyclicExecutive: Vector[Boolean]
) extends StandardDecisionModel
    with CompleteDecisionModel
    derives ReadWriter {

  val coveredElements =
    (processors ++ schedulers).toSet ++ (processors.zip(schedulers).toSet).map(_.toString)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  def bodyAsText: String       = write(this)
  val category: String = "PartitionedCoresWithRuntimes"

}
