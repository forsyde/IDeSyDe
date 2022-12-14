package idesyde.identification.common.models.platform

import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational

final case class SchedulableTiledMultiCore(
    val hardware: TiledMultiCore,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Rational] {

  val coveredElements         = hardware.coveredElements ++ runtimes.coveredElements
  val coveredElementRelations = hardware.coveredElementRelations ++ runtimes.coveredElementRelations

  def processorsFrequency: Array[Long] = hardware.processorsFrequency
  def processorsProvisions: Array[Map[String, Map[String, spire.math.Rational]]] =
    hardware.processorsProvisions

  val uniqueIdentifier: String = "SchedulableTiledMultiCore"
}
