package idesyde.common

import upickle.default.*

import idesyde.core.CompleteDecisionModel

final case class SchedulableTiledMultiCore(
    val hardware: TiledMultiCoreWithFunctions,
    val runtimes: PartitionedCoresWithRuntimes
) extends StandardDecisionModel
    with InstrumentedPlatformMixin[Double]
    with CompleteDecisionModel
    derives ReadWriter {

  val coveredElements = hardware.coveredElements ++ runtimes.coveredElements

  def processorsFrequency: Vector[Long] = hardware.processorsFrequency
  def processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    hardware.processorsProvisions

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  def bodyAsText: String = write(this)

  val uniqueIdentifier: String = "SchedulableTiledMultiCore"
}
