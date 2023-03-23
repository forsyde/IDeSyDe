package idesyde.identification.common.models.platform

import upickle.default.*

import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational

final case class SchedulableTiledMultiCore(
    val hardware: TiledMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Double] derives ReadWriter {

  val coveredElements         = hardware.coveredElements ++ runtimes.coveredElements
  val coveredElementRelations = hardware.coveredElementRelations ++ runtimes.coveredElementRelations

  def processorsFrequency: Vector[Long] = hardware.processorsFrequency
  def processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    hardware.processorsProvisions

  val uniqueIdentifier: String = "SchedulableTiledMultiCore"
}
