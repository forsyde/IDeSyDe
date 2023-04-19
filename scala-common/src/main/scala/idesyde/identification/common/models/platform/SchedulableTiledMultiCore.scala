package idesyde.identification.common.models.platform

import upickle.default.*

import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational
import idesyde.core.CompleteDecisionModel

final case class SchedulableTiledMultiCore(
    val hardware: TiledMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Double] with CompleteDecisionModel derives ReadWriter {

  val coveredElements         = hardware.coveredElements ++ runtimes.coveredElements
  val coveredElementRelations = hardware.coveredElementRelations ++ runtimes.coveredElementRelations

  def processorsFrequency: Vector[Long] = hardware.processorsFrequency
  def processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    hardware.processorsProvisions

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  def bodyAsText: String = write(this)

  val uniqueIdentifier: String = "SchedulableTiledMultiCore"
}
